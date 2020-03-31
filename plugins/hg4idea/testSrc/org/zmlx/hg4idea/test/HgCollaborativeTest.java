/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea.test;

import com.intellij.testFramework.RunAll;

/**
 * The parent of all tests, where at least two repositories communicate with each other.
 * This is used to test collaborative tasks, such as push, pull, merge and others.
 * @author Kirill Likhodedov
 */
public class HgCollaborativeTest extends HgTest {

  protected HgTestRepository myParentRepo;
  protected HgTestRepository myRepo;

  @Override
  protected HgTestRepository initRepositories() throws Exception {
    myParentRepo = HgTestRepository.create(this);
    myRepo = myParentRepo.cloneRepository();
    return myRepo;
  }

  @Override
  protected void tearDownRepositories() throws Exception {
    new RunAll(() -> myRepo.getDirFixture().tearDown(),
               () -> myParentRepo.getDirFixture().tearDown())
      .run();
  }

}