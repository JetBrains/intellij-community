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
package com.intellij.history.integration;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.changes.CreateEntryChange;
import com.intellij.history.core.changes.RenameChange;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.io.IOException;

public class VisitingTest extends IntegrationTestCase {
  @Test
  public void testSimpleVisit() throws Exception {
    createFile("f.txt");
    createFile("dir");
    assertVisitorLog("begin create end begin create end begin create end finished ");
  }

  @Test
  public void testVisitChangeSet() throws Exception {
    getVcs().beginChangeSet();
    createFile("f.txt");
    createFile("dir");
    getVcs().endChangeSet(null);

    assertVisitorLog("begin create create end begin create end finished ");
  }

  @Test
  public void testVisitingChangesInNotFinishedChangeSet() throws Exception {
    getVcs().beginChangeSet();
    createFile("f.txt");
    createFile("dir");

    assertVisitorLog("begin create create end begin create end finished ");
  }

  @Test
  public void testVisitingAllChanges() throws Exception {
    createFile("f.txt");
    getVcs().beginChangeSet();
    VirtualFile dir = createFile("dir");
    getVcs().endChangeSet(null);
    getVcs().beginChangeSet();
    rename(dir, "newDir");

    assertVisitorLog("begin rename end begin create end begin create end begin create end finished ");
  }

  @Test
  public void testStop() throws Exception {
    createFile("f.txt");
    createFile("dir");

    TestVisitor visitor = new TestVisitor() {
      int count = 0;

      @Override
      public void begin(ChangeSet c) throws ChangeVisitor.StopVisitingException {
        if (++count == 2) stop();
        super.begin(c);
      }
    };

    assertVisitorLog("begin create end finished ", visitor);
  }

  @Test
  public void testAddingChangesWhileVisiting() throws Exception {
    getVcs().beginChangeSet();
    createFile("f.txt");
    createFile("dir");
    getVcs().endChangeSet(null);

    final int[] count = {0};
    TestVisitor visitor = new TestVisitor() {
      @Override
      public void visit(CreateEntryChange c) throws StopVisitingException {
        super.visit(c);
        count[0]++;
        try {
          createFile("f" + count[0]);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };

    assertVisitorLog("begin create create end begin create end finished ", visitor);
    assertEquals(3, count[0]);
  }

  @Test
  public void testPurgingDuringVisit() throws Exception {
    Clock.setTime(10);
    getVcs().beginChangeSet();
    createFile("f.txt");
    getVcs().endChangeSet(null);

    Clock.setTime(20);
    getVcs().beginChangeSet();
    createFile("dir");
    getVcs().endChangeSet(null);

    TestVisitor visitor = new TestVisitor();
    getVcs().accept(visitor);
    assertEquals("begin create end begin create end begin create end finished ", visitor.log);

    visitor = new TestVisitor() {
      @Override
      public void visit(CreateEntryChange c) throws StopVisitingException {
        super.visit(c);
        getVcs().getChangeListInTests().purgeObsolete(5);
      }
    };

    // processing full list in spite of purging
    assertVisitorLog("begin create end begin create end finished ", visitor);

    visitor.log = "";
    getVcs().getChangeListInTests().purgeObsolete(5);
    assertVisitorLog("begin create end finished ", visitor);
  }

  private void assertVisitorLog(final String expected) {
    TestVisitor visitor = new TestVisitor();
    assertVisitorLog(expected, visitor);
  }

  private void assertVisitorLog(String expected, TestVisitor visitor) {
    getVcs().accept(visitor);
    assertEquals(expected, visitor.log);
  }

  private class TestVisitor extends ChangeVisitor {
    public String log = "";

    @Override
    public void begin(ChangeSet c) throws StopVisitingException {
      log += "begin ";
    }

    @Override
    public void end(ChangeSet c) {
      log += "end ";
    }

    @Override
    public void visit(CreateEntryChange c) throws StopVisitingException {
      log += "create ";
    }

    @Override
    public void visit(RenameChange c) {
      log += "rename ";
    }

    @Override
    public void finished() {
      log += "finished ";
    }
  }
}