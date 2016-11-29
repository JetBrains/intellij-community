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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChangeList {
  private static final Logger LOG = Logger.getInstance(ChangeList.class);
  public static final Comparator<Change> CHANGE_ORDER = new SimpleChange.ChangeOrder(FragmentSide.SIDE1);

  private final Project myProject;
  private final Document[] myDocuments = new Document[2];
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private ArrayList<Change> myChanges;
  private ArrayList<Change> myAppliedChanges;

  public ChangeList(@NotNull Document base, @NotNull Document version, @Nullable Project project) {
    myDocuments[0] = base;
    myDocuments[1] = version;
    myProject = project;
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(Listener listener) {
    LOG.assertTrue(myListeners.remove(listener));
  }

  public void setChanges(@NotNull ArrayList<Change> changes) {
    if (myChanges != null) {
      HashSet<Change> newChanges = new HashSet<>(changes);
      LOG.assertTrue(newChanges.size() == changes.size());
      for (Iterator<Change> iterator = myChanges.iterator(); iterator.hasNext();) {
        Change oldChange = iterator.next();
        if (!newChanges.contains(oldChange)) {
          iterator.remove();
          oldChange.onRemovedFromList();
        }
      }
    }
    for (Change change : changes) {
      LOG.assertTrue(change.isValid());
    }
    myChanges = new ArrayList<>(changes);
    myAppliedChanges = new ArrayList<>();
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public List<Change> getChanges() {
    return new ArrayList<>(myChanges);
  }

  public static ChangeList build(@NotNull Document base, @NotNull Document version, @NotNull Project project) throws FilesTooBigForDiffException {
    ChangeList result = new ChangeList(base, version, project);
    ArrayList<Change> changes = result.buildChanges();
    Collections.sort(changes, CHANGE_ORDER);
    result.setChanges(changes);
    return result;
  }

  public void setMarkup(final Editor base, final Editor version) {
    Editor[] editors = {base, version};
    for (Change change : myChanges) {
      change.addMarkup(editors);
    }
  }

  public void updateMarkup() {
    for (Change change : myChanges) {
      change.updateMarkup();
    }
  }

  @NotNull
  public Document getDocument(@NotNull FragmentSide side) {
    return myDocuments[side.getIndex()];
  }

  private ArrayList<Change> buildChanges() throws FilesTooBigForDiffException {
    Document base = getDocument(FragmentSide.SIDE1);
    DiffString[] baseLines = DiffUtil.convertToLines(base.getText());
    Document version = getDocument(FragmentSide.SIDE2);
    DiffString[] versionLines = DiffUtil.convertToLines(version.getText());
    DiffFragment[] fragments = ComparisonPolicy.DEFAULT.buildDiffFragmentsFromLines(baseLines, versionLines);
    final ArrayList<Change> result = new ArrayList<>();
    new DiffFragmentsEnumerator(fragments) {
      @Override
      protected void process(DiffFragment fragment) {
        if (fragment.isEqual()) return;
        Context context = getContext();
        TextRange range1 = context.createRange(FragmentSide.SIDE1);
        TextRange range2 = context.createRange(FragmentSide.SIDE2);
        result.add(new SimpleChange(ChangeType.fromDiffFragment(context.getFragment()), range1, range2, ChangeList.this));
      }
    }.execute();
    return result;
  }

  public Change getChange(int index) {
    return myChanges.get(index);
  }

  private abstract static class DiffFragmentsEnumerator {
    @NotNull private final DiffFragment[] myFragments;
    @NotNull private final Context myContext;

    private DiffFragmentsEnumerator(@NotNull DiffFragment[] fragments) {
      myContext = new Context();
      myFragments = fragments;
    }

    public void execute() {
      for (DiffFragment fragment : myFragments) {
        myContext.myFragment = fragment;
        process(fragment);
        DiffString text1 = fragment.getText1();
        DiffString text2 = fragment.getText2();
        myContext.myStarts[0] += StringUtil.length(text1);
        myContext.myStarts[1] += StringUtil.length(text2);
        myContext.myLines[0] += countLines(text1);
        myContext.myLines[1] += countLines(text2);
      }
    }

    private static int countLines(@Nullable DiffString text) {
      if (text == null) return 0;
      return StringUtil.countNewLines(text);
    }

    @NotNull
    protected Context getContext() {
      return myContext;
    }

    protected abstract void process(DiffFragment fragment);
  }

  public static class Context {
    private DiffFragment myFragment;
    private final int[] myStarts = {0, 0};
    private final int[] myLines = {0, 0};

    public DiffFragment getFragment() {
      return myFragment;
    }

    public int getStart(@NotNull FragmentSide side) {
      return myStarts[side.getIndex()];
    }

    public int getEnd(@NotNull FragmentSide side) {
      return getStart(side) + StringUtil.length(side.getText(myFragment));
    }

    @NotNull
    public TextRange createRange(@NotNull FragmentSide side) {
      return new TextRange(getStart(side), getEnd(side));
    }
  }

  public int getCount() {
    return myChanges.size();
  }

  public LineBlocks getNonAppliedLineBlocks() {
    ArrayList<Change> changes = new ArrayList<>(myChanges);
    return LineBlocks.fromChanges(changes);
  }

  public LineBlocks getLineBlocks() {
    ArrayList<Change> changes = new ArrayList<>(myChanges);
    changes.addAll(myAppliedChanges);
    return LineBlocks.fromChanges(changes);
  }

  public void remove(@NotNull Change change) {
    if (change.getType().isApplied()) {
      LOG.assertTrue(myAppliedChanges.remove(change), change);
    }
    else {
      LOG.assertTrue(myChanges.remove(change), change);
    }
    change.onRemovedFromList();
    fireOnChangeRemoved();
  }

  public void apply(@NotNull Change change) {
    LOG.assertTrue(myChanges.remove(change), change);
    myAppliedChanges.add(change);
    fireOnChangeApplied();
  }

  private void fireOnChangeRemoved() {
    for (Listener listener : myListeners) {
      listener.onChangeRemoved(this);
    }
  }

  void fireOnChangeApplied() {
    for (Listener listener : myListeners) {
      listener.onChangeApplied(this);
    }
  }

  public interface Listener {
    void onChangeRemoved(ChangeList source);
    void onChangeApplied(ChangeList source);
  }
}
