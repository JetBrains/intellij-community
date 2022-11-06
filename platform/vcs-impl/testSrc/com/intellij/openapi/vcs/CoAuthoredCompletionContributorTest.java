// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Set;

public class CoAuthoredCompletionContributorTest extends LightPlatformCodeInsightFixture4TestCase {
  @Test
  public void testCoAuthored() {
    configure("Test\n\nCo-<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            Test

                            Co-authored-by:\s""");
  }

  @Test
  public void testSignedOff() {
    configure("Test\n\nSig<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            Test

                            Signed-off-by:\s""");
  }

  @Test
  public void testNoCoAuthoredAtStart() {
    configure("Co-<caret>");
    assertEquals(0, myFixture.completeBasic().length);
  }

  @Test
  public void testNoCoAuthoredAtLineMiddle() {
    configure("Test\n\nHello Co-<caret>");
    assertEquals(0, myFixture.completeBasic().length);
  }

  @Test
  public void testAuthors() {
    ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(),
                                                          VcsUserRegistry.class,
                                                          new MockVcsUserRegistry(),
                                                          getTestRootDisposable());
    configure("Test\n\nCo-authored-by: x<caret>");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "xyz <xyz@example.com>", "xyz2 <xyz2@example.com>");
  }

  private void configure(String text) {
    myFixture.configureByText("a.txt", text);
    var commitMessage = new CommitMessage(getProject());
    Disposer.register(myFixture.getTestRootDisposable(), commitMessage);
    myFixture.getEditor().getDocument().putUserData(CommitMessage.DATA_KEY, commitMessage);
  }

  private static class MockVcsUserRegistry implements VcsUserRegistry {
    @Override
    public @NotNull Set<VcsUser> getUsers() {
      return Set.of(createUser("xyz", "xyz@example.com"),
                    createUser("xyz2", "xyz2@example.com"),
                    createUser("abc", "abc@example.com"));
    }

    @Override
    public @NotNull VcsUser createUser(@NotNull String name, @NotNull String email) {
      return new VcsUserImpl(name, email);
    }
  }
}
