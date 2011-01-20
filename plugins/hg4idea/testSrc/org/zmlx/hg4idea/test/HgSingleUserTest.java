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