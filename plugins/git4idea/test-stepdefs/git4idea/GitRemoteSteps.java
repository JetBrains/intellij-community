/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.GitHttpAuthenticator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static git4idea.GitCucumberWorld.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Kirill Likhodedov
 */
public class GitRemoteSteps {

  private TestAuthenticator myAuthenticator;
  private CountDownLatch myCloneCompleted = new CountDownLatch(1);

  @When("I clone (\\S+)")
  public void I_clone_the_repository(final String url) {
    myAuthenticator = new TestAuthenticator();

    myHttpAuthService.register(myAuthenticator);

    executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        String projectName = url.substring(url.lastIndexOf('/') + 1).replace(".git", "");
        GitCheckoutProvider.doClone(myProject, myGit, projectName, myTestRoot, url);
        myCloneCompleted.countDown();
      }
    });
  }

  @Then("I should be asked for the password")
  public void I_should_be_asked_for_the_password() throws InterruptedException {
    myAuthenticator.waitUntilPasswordIsAsked();
    assertTrue("Password was not requested", myAuthenticator.wasPasswordAsked());
  }

  @Then("I should be asked for the username")
  public void I_should_be_asked_for_the_username() throws InterruptedException {
    myAuthenticator.waitUntilUsernameIsAsked();
    assertTrue("Password was not requested", myAuthenticator.wasUsernameAsked());
  }

  @When("I provide password '(\\S+)'")
  public void I_provide_password(String password) {
    myAuthenticator.supplyPassword(password);
  }

  @When("I provide username '(\\S+)'")
  public void I_provide_username(String username) {
    myAuthenticator.supplyUsername(username);
  }

  @Then("repository should (not )?be cloned to (\\S+)")
  public void the_repository_should_be_cloned(String negation, String dir) throws InterruptedException {
    assertTrue("Clone didn't complete during the reasonable period of time", myCloneCompleted.await(5, TimeUnit.SECONDS));
    if (negation == null) {
      assertTrue("Repository directory was not found", new File(myTestRoot, dir).exists());
    }
    else {
      assertFalse("Repository directory shouldn't exist", new File(myTestRoot, dir).exists());
    }
  }

  private static class TestAuthenticator implements GitHttpAuthenticator {

    private static final int TIMEOUT = 10;

    private final CountDownLatch myPasswordAskedWaiter = new CountDownLatch(1);
    private final CountDownLatch myUsernameAskedWaiter = new CountDownLatch(1);
    private final CountDownLatch myPasswordSuppliedWaiter = new CountDownLatch(1);
    private final CountDownLatch myUsernameSuppliedWaiter = new CountDownLatch(1);

    private volatile boolean myPasswordAsked;
    private volatile boolean myUsernameAsked;

    private volatile String myPassword;
    private volatile String myUsername;

    @NotNull
    @Override
    public String askPassword(@NotNull String url) {
      myPasswordAsked  = true;
      myPasswordAskedWaiter.countDown();
      try {
        assertTrue("Password was not supplied during the reasonable period of time",
                   myPasswordSuppliedWaiter.await(TIMEOUT, TimeUnit.SECONDS));
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      return myPassword;
    }

    @NotNull
    @Override
    public String askUsername(@NotNull String url) {
      myUsernameAsked  = true;
      myUsernameAskedWaiter.countDown();
      try {
        assertTrue("Password was not supplied during the reasonable period of time",
                   myUsernameSuppliedWaiter.await(TIMEOUT, TimeUnit.SECONDS));
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      return myUsername;
    }


    void supplyPassword(@NotNull String password) {
      myPassword = password;
      myPasswordSuppliedWaiter.countDown();
    }

    void supplyUsername(@NotNull String username) {
      myUsername = username;
      myUsernameSuppliedWaiter.countDown();
    }

    void waitUntilPasswordIsAsked() throws InterruptedException {
      assertTrue("Password was not asked during the reasonable period of time",
                 myPasswordAskedWaiter.await(TIMEOUT, TimeUnit.SECONDS));
    }

    void waitUntilUsernameIsAsked() throws InterruptedException {
      assertTrue("Username was not asked during the reasonable period of time",
                 myUsernameAskedWaiter.await(TIMEOUT, TimeUnit.SECONDS));
    }

    @Override
    public void saveAuthData() {
    }

    @Override
    public void forgetPassword() {
    }

    @Override
    public boolean wasCancelled() {
      return false;
    }

    boolean wasPasswordAsked() {
      return myPasswordAsked;
    }

    boolean wasUsernameAsked() {
      return myUsernameAsked;
    }
  }
}

