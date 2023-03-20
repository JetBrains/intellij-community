/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.EditorViewAccessor;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class LogicalPositionCacheStressTest extends AbstractEditorTest {
  private static final int ITERATIONS = 10000;
  private static final Long SEED_OVERRIDE = null; // set non-null value to run with a specific seed

  private final Random myRandom = new Random() {{
    //noinspection ConstantConditions
    setSeed(mySeed = SEED_OVERRIDE == null ? nextLong() : SEED_OVERRIDE);
  }};
  private long mySeed;

  public void testRandomActions() {
    List<? extends Action> actions = Arrays.asList(new AddText(),
                                                     new RemoveText(),
                                                     new ReplaceText(),
                                                     new MoveText());
    LOG.debug("Seed is " + mySeed);
    int i = 0;
    try {
      initText("");
      for (i = 1; i <= ITERATIONS; i++) {
        doRandomAction(actions);
        checkConsistency(getEditor());
      }
    }
    catch (Throwable t) {
      String message = "Failed when run with seed=" + mySeed + " in iteration " + i;
      System.err.println(message);
      throw new RuntimeException(message, t);
    }
  }

  private void doRandomAction(List<? extends Action> actions) {
    actions.get(myRandom.nextInt(Arrays.asList(new AddText(),
                                               new RemoveText(),
                                               new ReplaceText(),
                                               new MoveText()).size())).perform(getEditor(), myRandom);
  }

  private static void checkConsistency(Editor editor) {
    checkLogicalPositionCache(editor);
  }

  private static void checkLogicalPositionCache(Editor editor) {
    EditorViewAccessor.getView(editor).getLogicalPositionCache().validateState();
  }

  private static CharSequence generateText(Random random) {
    int textLength = random.nextInt(10);
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < textLength; i++) {
      b.append(switch (random.nextInt(5)) {
        case 0 -> '\t';
        case 1 -> '\n';
        default -> ' ';
      });
    }
    return b;
  }
  
  @FunctionalInterface
  interface Action {
    void perform(Editor editor, Random random);
  }
  
  private class AddText implements Action {
    @Override
    public void perform(Editor editor, Random random) {
      Document document = editor.getDocument();
      int offset = random.nextInt(document.getTextLength() + 1);
      CharSequence text = generateText(random);
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(offset, text));
    }
  }

  private class RemoveText implements Action {
    @Override
    public void perform(Editor editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int from = random.nextInt(textLength + 1);
      int to = random.nextInt(textLength + 1);
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(Math.min(from, to), Math.max(from, to)));
    }
  }

  private class ReplaceText implements Action {
    @Override
    public void perform(Editor editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int from = random.nextInt(textLength + 1);
      int to = random.nextInt(textLength + 1);
      CharSequence text = generateText(random);
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(Math.min(from, to), Math.max(from, to), text));
    }
  }

  private class MoveText implements Action {
    @Override
    public void perform(Editor editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int[] offsets = {random.nextInt(textLength + 1), random.nextInt(textLength + 1), random.nextInt(textLength + 1)};
      Arrays.sort(offsets);
      if (offsets[0] == offsets[1] || offsets[1] == offsets[2]) return;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        if (random.nextBoolean()) {
          ((DocumentEx)document).moveText(offsets[0], offsets[1], offsets[2]);
        }
        else {
          ((DocumentEx)document).moveText(offsets[1], offsets[2], offsets[0]);
        }
      });
    }
  }
}
