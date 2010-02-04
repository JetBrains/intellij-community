/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.Util;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;

public interface DiffCorrection {
  DiffFragment[] correct(DiffFragment[] fragments);

  class TrueLineBlocks implements DiffCorrection, FragmentProcessor<FragmentsCollector> {
    private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.DiffCorrection.TrueLineBlocks");
    private final DiffPolicy myDiffPolicy;
    private final ComparisonPolicy myComparisonPolicy;

    public TrueLineBlocks(ComparisonPolicy comparisonPolicy) {
      myDiffPolicy = new DiffPolicy.LineBlocks(comparisonPolicy);
      myComparisonPolicy = comparisonPolicy;
    }

    public DiffFragment[] correct(DiffFragment[] fragments) {
      FragmentsCollector collector = new FragmentsCollector();
      collector.processAll(fragments, this);
      return collector.toArray();
    }

    public void process(DiffFragment fragment, FragmentsCollector collector) {
      if (!fragment.isEqual()) {
        if (myComparisonPolicy.isEqual(fragment))
          fragment = myComparisonPolicy.createFragment(fragment.getText1(), fragment.getText2());
        collector.add(fragment);
      } else {
        String[] lines1 = new LineTokenizer(fragment.getText1()).execute();
        String[] lines2 = new LineTokenizer(fragment.getText2()).execute();
        LOG.assertTrue(lines1.length == lines2.length);
        for (int i = 0; i < lines1.length; i++)
          collector.addAll(myDiffPolicy.buildFragments(lines1[i], lines2[i]));
      }
    }

    public DiffFragment[] correctAndNormalize(DiffFragment[] fragments) {
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

    public void process(DiffFragment fragment, FragmentsCollector collector) {
      if (!fragment.isChange()) {
        collector.add(fragment);
        return;
      }
      String text1 = fragment.getText1();
      String text2 = fragment.getText2();
      while (StringUtil.startsWithChar(text1, '\n') || StringUtil.startsWithChar(text2, '\n')) {
        String newLine1 = null;
        String newLine2 = null;
        if (StringUtil.startsWithChar(text1, '\n')) {
          newLine1 = "\n";
          text1 = text1.substring(1);
        }
        if (StringUtil.startsWithChar(text2, '\n')) {
          newLine2 = "\n";
          text2 = text2.substring(1);
        }
        collector.add(new DiffFragment(newLine1, newLine2));
      }
      String spaces1 = leadingSpaces(text1);
      String spaces2 = leadingSpaces(text2);
      if (spaces1.length() == 0 && spaces2.length() == 0) {
        DiffFragment trailing = myComparisonPolicy.createFragment(text1, text2);
        collector.add(trailing);
        return;
      }
      collector.addAll(myDiffPolicy.buildFragments(spaces1, spaces2));
      DiffFragment textFragment = myComparisonPolicy.createFragment(text1.substring(spaces1.length(), text1.length()),
                                                          text2.substring(spaces2.length(), text2.length()));
      collector.add(textFragment);
    }

    private String leadingSpaces(String text) {
      int i = 0;
      while (i < text.length() && text.charAt(i) == ' ') i++;
      return text.substring(0, i);
    }

    public DiffFragment[] correct(DiffFragment[] fragments) {
      FragmentsCollector collector = new FragmentsCollector();
      collector.processAll(fragments, this);
      return collector.toArray();
    }
  }

  interface FragmentProcessor<Collector> {
    void process(DiffFragment fragment, Collector collector);
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

    public void processAll(DiffFragment[] fragments, FragmentProcessor<ActualRunner> processor) {
      myFragments = fragments;
      for (;myIndex < myFragments.length; myIndex++) {
        DiffFragment fragment = myFragments[myIndex];
        processor.process(fragment, (ActualRunner)this);
      }
    }

    // todo think where
    public static int getTextLength(String text) {
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

    public void processAll(DiffFragment[] fragments, FragmentProcessor<FragmentBuffer> processor) {
      super.processAll(fragments, processor);
      flushMarked();
    }
  }

  class ConcatenateSingleSide implements DiffCorrection, FragmentProcessor<FragmentBuffer> {
    public static final DiffCorrection INSTANCE = new ConcatenateSingleSide();
    private static final int DEFAULT_MODE = 1;

    public DiffFragment[] correct(DiffFragment[] fragments) {
      FragmentBuffer buffer = new FragmentBuffer();
      buffer.processAll(fragments, this);
      return buffer.toArray();
    }

    public void process(DiffFragment fragment, FragmentBuffer buffer) {
      if (fragment.isOneSide()) buffer.markIfNone(DEFAULT_MODE);
      else buffer.add(fragment);
    }
  }

  class UnitEquals implements DiffCorrection, FragmentProcessor<FragmentBuffer> {
    public static final DiffCorrection INSTANCE = new UnitEquals();
    private static final int EQUAL_MODE = 1;
    private static final int FORMATTING_MODE = 2;

    public DiffFragment[] correct(DiffFragment[] fragments) {
      FragmentBuffer buffer = new FragmentBuffer();
      buffer.processAll(fragments, this);
      return buffer.toArray();
    }

    public void process(DiffFragment fragment, FragmentBuffer buffer) {
      if (fragment.isEqual()) buffer.markIfNone(EQUAL_MODE);
      else if (ComparisonPolicy.TRIM_SPACE.isEqual(fragment)) buffer.markIfNone(FORMATTING_MODE);
      else  buffer.add(fragment);
    }
  }

  class Normalize implements DiffCorrection {
    public static final DiffCorrection INSTANCE = new Normalize();

    private Normalize() {}

    public DiffFragment[] correct(DiffFragment[] fragments) {
      return UnitEquals.INSTANCE.correct(ConcatenateSingleSide.INSTANCE.correct(fragments));
    }
  }

  class ConnectSingleSideToChange implements DiffCorrection, FragmentProcessor<FragmentBuffer> {
    public static final ConnectSingleSideToChange INSTANCE = new ConnectSingleSideToChange();
    private static final int CHANGE = 1;

    public DiffFragment[] correct(DiffFragment[] fragments) {
      FragmentBuffer buffer = new FragmentBuffer();
      buffer.processAll(fragments, this);
      return buffer.toArray();
    }

    public void process(DiffFragment fragment, FragmentBuffer buffer) {
      if (fragment.isEqual()) buffer.add(fragment);
      else if (fragment.isOneSide()) {
        String text = FragmentSide.chooseSide(fragment).getText(fragment);
        if (StringUtil.endsWithChar(text, '\n'))
          buffer.add(fragment);
        else
          buffer.markIfNone(CHANGE);
      } else buffer.markIfNone(CHANGE);
    }
  }
}
