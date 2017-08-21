/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package hg4idea.test.validator;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryImpl;
import org.zmlx.hg4idea.util.HgBranchReferenceValidator;

import java.util.Collection;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.echo;
import static hg4idea.test.HgExecutor.hg;

@RunWith(Parameterized.class)
public class HgReferenceValidatorTest extends HgPlatformTest {

  private HgBranchReferenceValidator myValidator;
  private static final String BRANCH_NAME = "ABranch";
  private static final String UNCOMMITTED_BRANCH = "uncommitted new branch";

  @NotNull private final String myBranchName;
  private final boolean myExpected;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    HgRepository hgRepository = HgRepositoryImpl.getInstance(myRepository, myProject, myProject);
    assertNotNull(hgRepository);
    myValidator = new HgBranchReferenceValidator(hgRepository);
    cd(myRepository);
    hg("branch '" + BRANCH_NAME + "'");
    String firstFile = "file.txt";
    echo(firstFile, BRANCH_NAME);
    hg("commit -m 'createdBranch " + BRANCH_NAME + "' ");
    hg("branch '" + UNCOMMITTED_BRANCH + "'");
    hgRepository.update();
  }

  @Override
  @After
  public void tearDown() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        HgReferenceValidatorTest.super.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "UnusedParameters"})
  public HgReferenceValidatorTest(@NotNull String name, @NotNull String branchName, boolean expected) {
    myBranchName = branchName;
    myExpected = expected;
  }


  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> createData() {
    return ContainerUtil.newArrayList(new Object[][]{
      {"WORD", "branch", true},
      {"UNDERSCORED_WORD", "new_branch", true},
      {"HIERARCHY", "user/branch", true},
      {"HIERARCHY_2", "user/branch/sub_branch", true},
      {"BEGINS_WITH_SLASH", "/branch", true},
      {"WITH_DOTS", "complex.branch.name", true},
      {"WITH_WHITESPACES", "branch with whitespaces", true},
      {"WITH_SPECIAL_CHARS", "bra~nch-^%$", true},
      {"NOT_RESERVED", "TIP", true},
      {"CONTAINS_COLON", "bra:nch", false},
      {"ONLY_DIGITS", "876876", false},
      {"START_WITH_COLON", ":branch", false},
      {"ENDS_WITH_COLON", "branch:", false},
      {"RESERVED_WORD", "tip", false},
      {"BRANCH_CONFLICT", BRANCH_NAME, false},
      {"UNCOMMITTED_BRANCH_CONFLICT", UNCOMMITTED_BRANCH, false},
    });
  }

  @Test
  public void testValid() {
    assertEquals(" Wrong validation for " + myBranchName, myExpected, myValidator.checkInput(myBranchName));
    assertEquals(" Should be valid " + myBranchName, myExpected, myValidator.canClose(myBranchName));
  }
}
