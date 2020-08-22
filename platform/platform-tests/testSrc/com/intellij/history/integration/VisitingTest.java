// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.changes.CreateEntryChange;
import com.intellij.history.core.changes.RenameChange;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VisitingTest extends IntegrationTestCase {
  @Test
  public void testSimpleVisit() {
    createFile("f.txt");
    createFile("dir");
    assertVisitorLog("begin create end begin create end begin create end begin create end finished ");
  }

  @Test
  public void testVisitChangeSet() {
    getVcs().beginChangeSet();
    createFile("f.txt");
    createFile("dir");
    getVcs().endChangeSet(null);

    assertVisitorLog("begin create create end begin create end begin create end finished ");
  }

  @Test
  public void testVisitingChangesInNotFinishedChangeSet() {
    getVcs().beginChangeSet();
    createFile("f.txt");
    createFile("dir");

    assertVisitorLog("begin create create end begin create end begin create end finished");
  }

  @Test
  public void testVisitingAllChanges() {
    createFile("f.txt");
    getVcs().beginChangeSet();
    VirtualFile dir = createFile("dir");
    getVcs().endChangeSet(null);
    getVcs().beginChangeSet();
    rename(dir, "newDir");

    assertVisitorLog("begin rename end begin create end begin create end begin create end begin create end finished");
  }

  @Test
  public void testStop() {
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
  public void testAddingChangesWhileVisiting() {
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
        createFile("f" + count[0]);
      }
    };

    assertVisitorLog("begin create create end begin create end begin create end finished ", visitor);
    assertEquals(4, count[0]);
  }

  @Test
  public void testPurgingDuringVisit() {
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
    assertEquals("begin create end begin create end begin create end begin create end finished ", visitor.log);

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
    assertThat(visitor.log.trim()).isEqualTo(expected.trim());
  }

  private static class TestVisitor extends ChangeVisitor {
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