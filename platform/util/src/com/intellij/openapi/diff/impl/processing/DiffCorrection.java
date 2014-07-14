/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.Util;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public interface DiffCorrection {
  DiffFragment[] correct(DiffFragment[] fragments) throws FilesTooBigForDiffException;

  class TrueLineBlocks implements DiffCorrection, FragmentProcessor<FragmentsCollector> {
    private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.DiffCorrection.TrueLineBlocks");
    private final DiffPolicy myDiffPolicy;
    @NotNull private final ComparisonPolicy myComparisonPolicy;

    public TrueLineBlocks(@NotNull ComparisonPolicy comparisonPolicy) {
      myDiffPolicy = new DiffPolicy.LineBlocks(comparisonPolicy);
      myComparisonPolicy = comparisonPolicy;
    }

    @Override
    public DiffFragment[] correct(DiffFragment[] fragments) throws FilesTooBigForDiffException {
      FragmentsCollector collector = new FragmentsCollector();
      collector.processAll(fragments, this);
      return collector.toArray();
    }

    @Override
    public void process(@NotNull DiffFragment fragment, @NotNull FragmentsCollector collector) throws FilesTooBigForDiffException {
      DiffString text1 = fragment.getText1();
      DiffString text2 = fragment.getText2();
      if (!fragment.isEqual()) {
        if (myComparisonPolicy.isEqual(fragment))
          fragment = myComparisonPolicy.createFragment(text1, text2);
        collector.add(fragment);
      } else {
        assert text1 != null;
        assert text2 != null;
        DiffString[] lines1 = text1.tokenize();
        DiffString[] lines2 = text2.tokenize();
        LOG.assertTrue(lines1.length == lines2.length);
        for (int i = 0; i < lines1.length; i++)
          collector.addAll(myDiffPolicy.buildFragments(lines1[i], lines2[i]));
      }
    }

    public DiffFragment[] correctAndNormalize(DiffFragment[] fragments) throws FilesTooBigForDiffException {
      return Normalize.INSTANCE.correct(correct(fragments));
    }
  }

  class ChangedSpace implements DiffCorrection, FragmentProcessor<FragmentsCollector> {
    private final DiffPolicy myDiffPolicy;
    private final ComparisonPolicy myComparisonPolicy;

    public ChangedSpace(ComparisonPolicy policy) {
      myComparisonPolicy = policy;
      myDiffPolicy = new DiffPolicy.ByChar(myComparisonPolicy);
    }

    @Override
    public void process(@NotNull DiffFragment fragment, @NotNull FragmentsCollector collector) throws FilesTooBigForDiffException {
      if (!fragment.isChange()) {
        collector.add(fragment);
        return;
      }
      DiffString text1 = fragment.getText1();
      DiffString text2 = fragment.getText2();
      while (StringUtil.startsWithChar(text1, '\n') || StringUtil.startsWithChar(text2, '\n')) {
        DiffString newLine1 = null;
        DiffString newLine2 = null;
        if (StringUtil.startsWithChar(text1, '\n')) {
          newLine1 = DiffString.create("\n");
          text1 = text1.substring(1);
        }
        if (StringUtil.startsWithChar(text2, '\n')) {
          newLine2 = DiffString.create("\n");
          text2 = text2.substring(1);
        }
        collector.add(new DiffFragment(newLine1, newLine2));
      }
      DiffString spaces1 = text1.getLeadingSpaces();
      DiffString spaces2 = text2.getLeadingSpaces();
      if (spaces1.isEmpty() && spaces2.isEmpty()) {
        DiffFragment trailing = myComparisonPolicy.createFragment(text1, text2);
        collector.add(trailing);
        return;
      }
      collector.addAll(myDiffPolicy.buildFragments(spaces1, spaces2));
      DiffFragment textFragment = myComparisonPolicy
        .createFragment(text1.substring(spaces1.length(), text1.length()), text2.substring(spaces2.length(), text2.length()));
      collector.add(textFragment);
    }

    @Override
    public DiffFragment[] correct(DiffFragment[] fragments) throws FilesTooBigForDiffException {
      FragmentsCollector collector = new FragmentsCollector();
      collector.processAll(fragments, this);
      return collector.toArray();
    }
  }

  interface FragmentProcessor<Collector> {
    void process(@NotNull DiffFragment fragment, @NotNull Collector collector) throws FilesTooBigForDiffException;
  }

  class BaseFragmentRunner<ActualRunner extends BaseFragmentRunner> {
    private final ArrayList<DiffFragment> myItems = new ArrayList<DiffFragment>();
    private int myIndex = 0;
    private DiffFragment[] myFragments;

    public void add(DiffFragment fragment) {
      actualAdd(fragment);
    }

    protected final void actualAdd(DiffFragment fragment) {
      if (isEmpty(fragment)) return;
      myItems.add(fragment);
    }

    public DiffFragment[] toArray() {
      return myItems.toArray(new DiffFragment[myItems.size()]);
    }

    protected int getIndex() { return myIndex; }

    public DiffFragment[] getFragments() { return myFragments; }

    public void processAll(DiffFragment[] fragments, FragmentProcessor<ActualRunner> processor) throws FilesTooBigForDiffException {
      myFragments = fragments;
      for (;myIndex < myFragments.length; myIndex++) {
        DiffFragment fragment = myFragments[myIndex];
        processor.process(fragment, (ActualRunner)this);
      }
    }

    public static int getTextLength(DiffString text) {
      return text != null ? text.length() : 0;
    }

    public static boolean isEmpty(DiffFragment fragment) {
      return getTextLength(fragment.getText1()) == 0 &&
             getTextLength(fragment.getText2()) == 0;
    }

  }

  class FragmentsCollector extends BaseFragmentRunner<FragmentsCollector> {
    public void addAll(DiffFragment[] fragments) {
      for (int i = 0; i < fragments.length; i++) {
        add(fragments[i]);
      }
    }
  }

  class FragmentBuffer extends BaseFragmentRunner<FragmentBuffer> {
    private int myMark = -1;
    private int myMarkMode = -1;

    public void markIfNone(int mode) {
      if (mode == myMarkMode || myMark == -1) {
        if (myMark == -1) myMark = getIndex();
      } else {
        flushMarked();
        myMark = getIndex();
      }
      myMarkMode = mode;
    }

    @Override
    public void add(DiffFragment fragment) {
      flushMarked();
      super.add(fragment);
    }

    protected void flushMarked() {
      if (myMark != -1) {
        actualAdd(Util.concatenate(getFragments(), myMark, getIndex()));
        myMark = -1;
      }
    }

    @Override
    public void processAll(DiffFragment[] fragments, FragmentProcessor<FragmentBuffer> processor) throws FilesTooBigForDiffException {
      super.processAll(fragments, processor);
      flushMarked();
    }
  }

  class ConcatenateSingleSide implements DiffCorrection, FragmentProcessor<FragmentBuffer> {
    public static final DiffCorrection INSTANCE = new ConcatenateSingleSide();
    private static final int DEFAULT_MODE = 1;

    @Override
    public DiffFragment[] correct(DiffFragment[] fragments) throws FilesTooBigForDiffException {
      FragmentBuffer buffer = new FragmentBuffer();
      buffer.processAll(fragments, this);
      return buffer.toArray();
    }

    @Override
    public void process(@NotNull DiffFragment fragment, @NotNull FragmentBuffer buffer) {
      if (fragment.isOneSide()) buffer.markIfNone(DEFAULT_MODE);
      else buffer.add(fragment);
    }
  }

  class UnitEquals implements DiffCorrection, FragmentProcessor<FragmentBuffer> {
    public static final DiffCorrection INSTANCE = new UnitEquals();
    private static final int EQUAL_MODE = 1;
    private static final int FORMATTING_MODE = 2;

    @Override
    public DiffFragment[] correct(DiffFragment[] fragments) throws FilesTooBigForDiffException {
      FragmentBuffer buffer = new FragmentBuffer();
      buffer.processAll(fragments, this);
      return buffer.toArray();
    }

    @Override
    public void process(@NotNull DiffFragment fragment, @NotNull FragmentBuffer buffer) {
      if (fragment.isEqual()) buffer.markIfNone(EQUAL_MODE);
      else if (ComparisonPolicy.TRIM_SPACE.isEqual(fragment)) buffer.markIfNone(FORMATTING_MODE);
      else  buffer.add(fragment);
    }
  }

  class Normalize implements DiffCorrection {
    public static final DiffCorrection INSTANCE = new Normalize();

    private Normalize() {}

    @Override
    public DiffFragment[] correct(DiffFragment[] fragments) throws FilesTooBigForDiffException {
      return UnitEquals.INSTANCE.correct(ConcatenateSingleSide.INSTANCE.correct(fragments));
    }
  }

  class ConnectSingleSideToChange implements DiffCorrection, FragmentProcessor<FragmentBuffer> {
    public static final ConnectSingleSideToChange INSTANCE = new ConnectSingleSideToChange();
    private static final int CHANGE = 1;

    @Override
    public DiffFragment[] correct(DiffFragment[] fragments) throws FilesTooBigForDiffException {
      FragmentBuffer buffer = new FragmentBuffer();
      buffer.processAll(fragments, this);
      return buffer.toArray();
    }

    @Override
    public void process(@NotNull DiffFragment fragment, @NotNull FragmentBuffer buffer) {
      if (fragment.isEqual()) buffer.add(fragment);
      else if (fragment.isOneSide()) {
        DiffString text = FragmentSide.chooseSide(fragment).getText(fragment);
        if (StringUtil.endsWithChar(text, '\n'))
          buffer.add(fragment);
        else
          buffer.markIfNone(CHANGE);
      } else buffer.markIfNone(CHANGE);
    }
  }
}
