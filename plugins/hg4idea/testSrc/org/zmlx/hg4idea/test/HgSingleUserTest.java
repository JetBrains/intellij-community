// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.test;

/**
 * HgSingleUserTest is the parent of test cases for single user workflow.
 * It doesn't include collaborate tasks such as cloning, pushing, etc.
 * @author Kirill Likhodedov
 */
public class HgSingleUserTest extends HgTest {
  protected HgTestRepository myRepo;

  @Override
  protected HgTestRepository initRepositories() throws Exception {
    myRepo = HgTestRepository.create(this);
    return myRepo;
  }

  @Override
  protected void tearDownRepositories() throws Exception {
    myRepo.getDirFixture().tearDown();
  }
}