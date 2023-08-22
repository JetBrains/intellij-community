/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.history.integration.revertion;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class SelectionReverterTest extends IntegrationTestCase {
  private VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = createChildData(myRoot, "f.txt");
  }

  public void testBasics() throws Exception {
    String before = """
      public class Bar {
        public String foo() {
          return "old";
        }
      }
      """;
    String after = """
      public class NewBar {
        public String foo() {
          return "new";
        }
        public abstract bar();
      }
      """;

    setBinaryContent(f, before.getBytes(StandardCharsets.UTF_8));
    setBinaryContent(f, after.getBytes(StandardCharsets.UTF_8));

    revertToPreviousRevision(2, 2);
    
    String expected = """
      public class NewBar {
        public String foo() {
          return "old";
        }
        public abstract bar();
      }
      """;
    assertEquals(expected, new String(f.contentsToByteArray(), StandardCharsets.UTF_8));
  }

  public void testChangeSetName() throws Exception {
    long time = new Date(2001, Calendar.FEBRUARY, 11, 12, 30).getTime();
    Clock.setTime(time);

    setBinaryContent(f, "one".getBytes(StandardCharsets.UTF_8));
    setBinaryContent(f, "two".getBytes(StandardCharsets.UTF_8));

    revertToPreviousRevision(0, 0);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(5, rr.size());
    assertEquals("Reverted to " + DateFormatUtil.formatDateTime(time), rr.get(1).getChangeSetName());
  }

  public void testAskingForReadOnlyStatusClearingOnlyForTheSpecifiedFile() throws Exception {
    createChildData(myRoot, "foo1.txt");
    setBinaryContent(f, "one".getBytes(StandardCharsets.UTF_8));
    createChildData(myRoot, "foo2.txt");
    setBinaryContent(f, "two".getBytes(StandardCharsets.UTF_8));
    createChildData(myRoot, "foo3.txt");

    final List<VirtualFile> files = new ArrayList<>();
    myGateway = new IdeaGateway() {
      @Override
      public boolean ensureFilesAreWritable(@NotNull Project p, @NotNull List<? extends VirtualFile> ff) {
        files.addAll(ff);
        return true;
      }
    };

    List<String> errors = checkCanRevertToPreviousRevision();
    assertTrue(errors.isEmpty());

    assertEquals(1, files.size());
    assertEquals(f, files.get(0));
  }

  private void revertToPreviousRevision(int from, int to) throws Exception {
    createReverter(from, to).revert();
  }

  private List<String> checkCanRevertToPreviousRevision() throws IOException {
    return createReverter(0, 0).checkCanRevert();
  }

  private SelectionReverter createReverter(int from, int to) {
    List<Revision> rr = getRevisionsFor(f);
    SelectionCalculator c = new SelectionCalculator(myGateway, rr, from, to);
    Revision leftRev = rr.get(1);
    Entry entry = getRootEntry().getEntry(f.getPath());

    return new SelectionReverter(myProject, getVcs(), myGateway, c, leftRev, entry, from, to);
  }
}
