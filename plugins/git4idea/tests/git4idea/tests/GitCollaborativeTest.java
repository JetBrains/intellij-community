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
package git4idea.tests;

/**
 * 
 * @author Kirill Likhodedov
 */
public class GitCollaborativeTest extends GitTest {
  public static final String MAIN_USER_NAME = "John Smith";
  public static final String MAIN_USER_EMAIL = "john.smith@email.com";
  public static final String BROTHER_USER_NAME = "Bob Doe";
  public static final String BROTHER_USER_EMAIL = "bob.doe@email.com";

  protected GitTestRepository myRepo;         // main repository with IDEA project
  protected GitTestRepository myParentRepo;  // bare 'central' repository
  protected GitTestRepository myBrotherRepo; // another developers repository

  @Override
  protected GitTestRepository initRepositories() throws Exception {
    myParentRepo = GitTestRepository.create(this, "--bare");
    myRepo = GitTestRepository.cloneFrom(myParentRepo);
    myRepo.setName(MAIN_USER_NAME, MAIN_USER_EMAIL);
    myBrotherRepo = GitTestRepository.cloneFrom(myParentRepo);
    myBrotherRepo.setName(BROTHER_USER_NAME, BROTHER_USER_EMAIL);
    return myRepo;
  }

  @Override
  protected void tearDownRepositories() throws Exception {
    myRepo.getDirFixture().tearDown();
    myParentRepo.getDirFixture().tearDown();
    myBrotherRepo.getDirFixture().tearDown();
  }

}
