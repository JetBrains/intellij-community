package git4idea.branch;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitVcs;
import git4idea.repo.GitRepository;
import git4idea.test.*;
import git4idea.tests.TestDialogHandler;
import git4idea.tests.TestDialogManager;
import git4idea.util.UntrackedFilesNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.test.GitExec.*;
import static git4idea.util.GitUIUtil.getShortRepositoryName;
import static org.testng.Assert.*;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchOperationsTest extends AbstractVcsTestCase  {

  private static final String NEW_BRANCH = "new_branch";
  private static final String MASTER = "master";

  private List<GitRepository> myRepositories;
  private GitRepository myUltimate;
  private GitRepository myCommunity;
  private GitRepository myContrib;

  private TestMessageManager myMessageManager;
  private TestNotificator myNotificationManager;
  private TestDialogManager myDialogManager;
  
  private TempDirTestFixture myTempDirFixture;

  @BeforeMethod
  public void setup(final Method testMethod) throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    myTempDirFixture = fixtureFactory.createTempDirTestFixture();
    myTempDirFixture.setUp();
    
    final File projectDir = new File(myTempDirFixture.getTempDirPath(), "ultimate");
    assertTrue(projectDir.mkdir());
    
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          initProject(projectDir, testMethod.getName());
          initRepositories(VcsUtil.getVirtualFile(projectDir));
        }
        catch (Exception e) {
          throw new RuntimeException("Exception initializing the test", e);
        }
      }
    });

    GitVcs vcs = GitVcs.getInstance(myProject);
    assertNotNull(vcs);
    myTraceClient = true;
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD);
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE);

    myNotificationManager = GitTestUtil.registerNotificationManager(myProject);
    myMessageManager = GitTestUtil.registerMessageManager(myProject);
    GitTestPlatformFacade platformFacade = GitTestUtil.registerPlatformFacade(myProject);
    myDialogManager = platformFacade.getDialogManager();

    createAddCommit(myUltimate, "a");
    createAddCommit(myCommunity, "a");
    createAddCommit(myContrib, "a");

    myUltimate.getRoot().refresh(false, true);
  }
  
  protected void doActionSilently(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
  }
  
  @AfterMethod
  public void tearDown() throws Exception {
    if (myTempDirFixture != null) {
      myTempDirFixture.tearDown();
      myTempDirFixture = null;
    }
  }

  private void initRepositories(VirtualFile projectDir) throws IOException {
    myUltimate = init(myProject, projectDir);
    VirtualFile communityDir = createDirInCommand(projectDir, "community");
    VirtualFile contribDir = createDirInCommand(projectDir, "contrib");
    myCommunity = init(myProject, communityDir);
    myContrib = init(myProject, contribDir);

    addProjectRoots();
    myRepositories = Arrays.asList(myUltimate, myCommunity, myContrib);
  }

  private void addProjectRoots() {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    vcsManager.setDirectoryMapping(myUltimate.getRoot().getPath(), GitVcs.NAME);
    vcsManager.setDirectoryMapping(myCommunity.getRoot().getPath(), GitVcs.NAME);
    vcsManager.setDirectoryMapping(myContrib.getRoot().getPath(), GitVcs.NAME);
  }

  @Test
  public void create_new_branch_without_problems() throws Exception {
    doCheckoutNewBranch();
    assertNotify(NotificationType.INFORMATION, "Branch new_branch was created");
  }

  @Test
  public void create_new_branch_with_unmerged_files_in_first_repo_should_show_notification() throws Exception {
    GitTestScenarioGenerator.prepareUnmergedFiles(myUltimate);
    doCheckoutNewBranch();
    assertNotify(NotificationType.ERROR, unmergedFilesErrorNotificationDescription("checkout"));
  }

  @Test
  public void create_new_branch_with_unmerged_files_in_second_repo_should_propose_to_rollback() throws Exception {
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity, myContrib);
    doCheckoutNewBranch();
    assertMessage(unmergedFilesErrorTitle("checkout"));
  }

  @Test
  public void rollback_create_new_branch_should_delete_branch() throws Exception {
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity, myContrib);
    myMessageManager.nextAnswer(Messages.OK);
    doCheckoutNewBranch();
    assertMessage(unmergedFilesErrorTitle("checkout"));
    assertBranch("master");
    assertTrue(!branch(myUltimate).contains(NEW_BRANCH));
  }

  @Test
  public void deny_rollback_create_new_branch() throws Exception {
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity, myContrib);
    myMessageManager.nextAnswer(Messages.CANCEL);
    doCheckoutNewBranch();
    assertMessage(unmergedFilesErrorTitle("checkout"));

    assertBranch(myUltimate, NEW_BRANCH);
    assertBranch(myCommunity, MASTER);
    assertBranch(myContrib, MASTER);
  }

  @Test
  public void checkout_without_problems() throws Exception {
    prepareBranchWithCommit("feature");
    doCheckout("feature", null);
    assertNotify(NotificationType.INFORMATION, "Checked out feature");
  }

  @Test
  public void checkout_with_unmerged_files_in_first_repo_should_show_notification() throws Exception {
    prepareBranchWithCommit("feature");
    GitTestScenarioGenerator.prepareUnmergedFiles(myUltimate);
    doCheckout("feature", null);
    assertNotify(NotificationType.ERROR, unmergedFilesErrorNotificationDescription("checkout"));
  }

  @Test
  public void checkout_with_unmerged_file_in_second_repo_should_propose_to_rollback() throws Exception {
    prepareBranchWithCommit("feature");
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    doCheckout("feature", null);
    assertMessage(unmergedFilesErrorTitle("checkout"));
  }

  @Test
  public void rollback_checkout_should_return_to_previous_branch() throws Exception {
    prepareBranchWithCommit("feature");
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    myMessageManager.nextAnswer(Messages.OK);
    doCheckout("feature", null);
    assertMessage(unmergedFilesErrorTitle("checkout"));
    assertBranch("master");
  }

  @Test
  public void deny_rollback_checkout_should_do_nothing() throws Exception {
    prepareBranchWithCommit("feature");
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    myMessageManager.nextAnswer(Messages.CANCEL);
    doCheckout("feature", null);
    assertMessage(unmergedFilesErrorTitle("checkout"));
    assertBranch(myUltimate, "feature");
    assertBranch(myCommunity, "master");
    assertBranch(myContrib, "master");
  }

  @Test
  public void checkout_with_untracked_files_overwritten_by_checkout_in_first_repo_should_show_notification() throws Exception {
    test_untracked_files_overwritten_by_in_first_repo(true);
  }

  private void test_untracked_files_overwritten_by_in_first_repo(boolean checkout) throws Exception {
    prepareUntrackedFilesAndBranchWithSameTrackedFiles(myUltimate);
    branch(myCommunity, "feature");
    branch(myContrib, "feature");

    doCheckoutOrMerge(checkout, "feature");
    String operation = checkout ? "checkout" : "merge";
    assertNotify(NotificationType.ERROR, UntrackedFilesNotifier.createUntrackedFilesOverwrittenDescription(operation, false));
  }

  @Test
  public void checkout_with_untracked_files_overwritten_by_checkout_in_second_repo_should_show_rollback_proposal_with_file_list() throws Exception {
    test_checkout_with_untracked_files_overwritten_by_in_second_repo(true);
  }

  public void test_checkout_with_untracked_files_overwritten_by_in_second_repo(boolean checkout) throws Exception {
    prepareUntrackedFilesAndBranchWithSameTrackedFiles(myCommunity);
    branch(myUltimate, "feature");
    branch(myContrib, "feature");

    Class gitCheckoutOperationClass = Class.forName("git4idea.branch.GitBranchOperation");
    Class[] classes = gitCheckoutOperationClass.getDeclaredClasses();
    Class untrackedFilesDialogClass = null;
    for (Class aClass : classes) {
      if (aClass.getName().endsWith("UntrackedFilesDialog")) {
        untrackedFilesDialogClass = aClass;
      }
    }
    assertNotNull(untrackedFilesDialogClass);

    final AtomicBoolean dialogShown = new AtomicBoolean();
    final Class finalUntrackedFilesDialogClass = untrackedFilesDialogClass;
    myDialogManager.registerDialogHandler(untrackedFilesDialogClass, new TestDialogHandler() {
      @Override
      public int handleDialog(Object dialog) {
        if (dialog.getClass().equals(finalUntrackedFilesDialogClass)) {
          dialogShown.set(true);
        }
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    doCheckoutOrMerge(checkout, "feature");
    assertTrue(dialogShown.get());
  }

  @Test
  public void checkout_with_local_changes_overwritten_by_checkout_should_show_smart_checkout_dialog() throws Exception {
    test_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog(true);
  }

  public void test_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog(boolean checkout) throws Exception {
    prepareLocalChangesAndBranchWithSameModifiedFilesWithoutConflicts(myUltimate);
    branch(myCommunity, "feature");
    branch(myContrib, "feature");

    final AtomicBoolean dialogShown = new AtomicBoolean();
    myDialogManager.registerDialogHandler(GitSmartOperationDialog.class, new TestDialogHandler<GitSmartOperationDialog>() {
      @Override
      public int handleDialog(GitSmartOperationDialog dialog) {
        dialogShown.set(true);
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    doCheckoutOrMerge(checkout, "feature");
    assertTrue(dialogShown.get());
  }

  @Test
  public void agree_to_smart_checkout_should_smart_checkout() throws Exception {
    prepare_agree_to_smart_operation(true);
    assertBranch("feature");
    for (GitRepository repository : myRepositories) {
      refresh(repository);
      assertBranch(repository, "feature");
      assertEquals(read(repository, "local.txt"), "master\ninitial content\nfeature content\n");
    }
  }

  public void prepare_agree_to_smart_operation(boolean checkout) throws Exception {
    prepareLocalChangesAndBranchWithSameModifiedFilesWithoutConflicts(myUltimate);
    prepareLocalChangesAndBranchWithSameModifiedFilesWithoutConflicts(myCommunity);
    prepareLocalChangesAndBranchWithSameModifiedFilesWithoutConflicts(myContrib);
    myDialogManager.registerDialogHandler(GitSmartOperationDialog.class,
      new TestDialogHandler<GitSmartOperationDialog>() {
        @Override
        public int handleDialog(GitSmartOperationDialog dialog) {
          return DialogWrapper.OK_EXIT_CODE;
        }
    });

    doCheckoutOrMerge(checkout, "feature");
  }

  @Test
  public void deny_to_smart_checkout_in_first_repo_should_show_notification() throws Exception {
    test_deny_to_smart_operation_in_first_repo_should_show_notification(true);
  }

  public void test_deny_to_smart_operation_in_first_repo_should_show_notification(boolean checkout) throws Exception {
    prepareLocalChangesAndBranchWithSameModifiedFilesWithoutConflicts(myUltimate);
    branch(myCommunity, "feature");
    branch(myContrib, "feature");

    myDialogManager.registerDialogHandler(GitSmartOperationDialog.class, new TestDialogHandler<GitSmartOperationDialog>() {
      @Override
      public int handleDialog(GitSmartOperationDialog dialog) {
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    doCheckoutOrMerge(checkout, "feature");
    String operation = checkout ? "checkout" : "merge";
    assertBranch("master");
  }

  @Test
  public void deny_to_smart_checkout_in_second_repo_should_show_rollback_proposal() throws Exception {
    test_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal(true);
  }

  public void test_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal(boolean checkout) throws Exception {
    prepareLocalChangesAndBranchWithSameModifiedFilesWithoutConflicts(myCommunity);
    branch(myUltimate, "feature");
    branch(myContrib, "feature");
    myDialogManager.registerDialogHandler(GitSmartOperationDialog.class, new TestDialogHandler<GitSmartOperationDialog>() {
      @Override
      public int handleDialog(GitSmartOperationDialog dialog) {
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    doCheckoutOrMerge(checkout, "feature");
    String operationName = checkout ? "checkout" : "merge";
    String rollbackProposal = checkout ?
                              String.format(GitCheckoutOperation.ROLLBACK_PROPOSAL_FORMAT, "master") :
                              GitMergeOperation.ROLLBACK_PROPOSAL;
    assertMessage("Couldn't " + operationName + " feature",
                  "However " + operationName + " has succeeded for the following repository:<br/>" +
                  myUltimate.getPresentableUrl() +
                  "<br/>" + rollbackProposal,
                  "Rollback", "Don't rollback");
  }

  @Test
  public void rollback_checkout_branch_as_new_branch_should_delete_branches() throws Exception {
    prepareBranchWithCommit("feature");
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    myMessageManager.nextAnswer(Messages.OK);
    doCheckout("feature", "newBranch");
    assertMessage(unmergedFilesErrorTitle("checkout"));
    assertBranch("master");
    for (GitRepository repository : myRepositories) {
      assertFalse(branch(repository).contains("newBranch"), "Branch newBranch wasn't deleted from repository " + getShortRepositoryName(
        repository));
    }
  }

  private static void prepareLocalChangesAndBranchWithSameModifiedFilesWithoutConflicts(GitRepository repository) throws IOException {
    create(repository, "local.txt", "initial content\n");
    addCommit(repository);
    checkout(repository, "-b", "feature");
    edit(repository, "local.txt", "initial content\nfeature content\n");
    addCommit(repository);
    checkout(repository, "master");
    edit(repository, "local.txt", "master\ninitial content\n");
  }

  private static void prepareUntrackedFilesAndBranchWithSameTrackedFiles(GitRepository repository) throws IOException {
    checkout(repository, "-b", "feature");
    createAddCommit(repository, "untracked.txt");
    checkout(repository, "master");
    create(repository, "untracked.txt");
  }

  @Test
  public void delete_branch_without_problems() throws Exception {
    for (GitRepository repository : myRepositories) {
      branch(repository, "master1");
      refresh(repository);
    }
    doDeleteBranch("master1");
    assertNotify(NotificationType.INFORMATION, "Deleted branch master1");
  }

  @Test
  public void delete_unmerged_branch_should_show_dialog() throws Exception {
    prepareBranchWithCommit("unmerged_branch", myUltimate, myCommunity, myContrib);

    final AtomicBoolean dialogShown = new AtomicBoolean();
    myDialogManager.registerDialogHandler(GitBranchIsNotFullyMergedDialog.class, new TestDialogHandler<GitBranchIsNotFullyMergedDialog>() {
      @Override public int handleDialog(GitBranchIsNotFullyMergedDialog dialog) {
        dialogShown.set(true);
        return DialogWrapper.CANCEL_EXIT_CODE;
      }
    });

    doDeleteBranch("unmerged_branch");
    assertTrue(dialogShown.get());
  }

  @Test
  public void ok_in_unmerged_branch_dialog_should_force_delete_branch() throws Exception {
    prepareBranchWithCommit("unmerged_branch", myUltimate, myCommunity, myContrib);
    registerNotFullyMergedDialog(DialogWrapper.OK_EXIT_CODE);
    doDeleteBranch("unmerged_branch");
    for (GitRepository repository : myRepositories) {
      assertTrue(!branch(repository).contains("unmerged_branch"));
    }
  }

  @Test
  public void cancel_in_unmerged_branch_dialog_in_first_repository_should_show_notification() throws Exception {
    prepareBranchWithCommit("unmerged_branch", myUltimate, myContrib);
    branch(myCommunity, "unmerged_branch");

    registerNotFullyMergedDialog(DialogWrapper.CANCEL_EXIT_CODE);
    doDeleteBranch("unmerged_branch");
    assertNotify(NotificationType.ERROR, "Branch unmerged_branch wasn't deleted", "This branch is not fully merged to master.");
  }

  @Test
  public void cancel_in_unmerged_branch_dialog_in_not_first_repository_should_show_rollback_proposal() throws Exception {
    branch(myUltimate, "unmerged_branch");
    prepareBranchWithCommit("unmerged_branch", myCommunity, myContrib);

    registerNotFullyMergedDialog(DialogWrapper.CANCEL_EXIT_CODE);
    doDeleteBranch("unmerged_branch");
    assertMessage(String.format("Branch %s wasn't deleted", "unmerged_branch"));
  }

  @Test
  public void rollback_delete_branch_should_recreate_branches() throws Exception {
    branch(myUltimate, "unmerged_branch");
    prepareBranchWithCommit("unmerged_branch", myCommunity);
    branch(myContrib, "unmerged_branch");

    registerNotFullyMergedDialog(DialogWrapper.CANCEL_EXIT_CODE);
    myMessageManager.nextAnswer(Messages.OK);
    doDeleteBranch("unmerged_branch");

    for (GitRepository repository : myRepositories) {
      assertTrue(branch(repository).contains("unmerged_branch"));
    }
  }

  @Test
  public void deny_rollback_delete_branch_should_do_nothing() throws Exception {
    branch(myUltimate, "unmerged_branch");
    prepareBranchWithCommit("unmerged_branch", myCommunity);
    branch(myContrib, "unmerged_branch");

    registerNotFullyMergedDialog(DialogWrapper.CANCEL_EXIT_CODE);
    myMessageManager.nextAnswer(Messages.CANCEL);
    doDeleteBranch("unmerged_branch");

    assertTrue(branch(myCommunity).contains("unmerged_branch"));
    assertTrue(branch(myContrib).contains("unmerged_branch"));
    assertTrue(!branch(myUltimate).contains("unmerged_branch"));
  }

  @Test
  public void merge_simple_without_problems() throws Exception {
    prepareBranchWithCommit("feature", myUltimate, myCommunity, myContrib);
    doMerge("feature");
    assertNotify(NotificationType.INFORMATION, "Merged feature to master<br/>Delete feature");

    assertFile(myUltimate, "unmerged_branch_file", "content");
    assertFile(myCommunity, "unmerged_branch_file", "content");
    assertFile(myContrib, "unmerged_branch_file", "content");
  }

  private static void assertFile(GitRepository repository, String path, String content) throws IOException {
    VirtualFile branchFile = repository.getRoot().findChild(path);
    assertNotNull(branchFile);
    assertTrue(branchFile.exists());
    assertEquals(new String(branchFile.contentsToByteArray()), content);
  }

  @Test
  public void merge_up_to_date_branch() throws Exception {
    branch(myUltimate, "master2");
    branch(myCommunity, "master2");
    branch(myContrib, "master2");

    doMerge("master2");

    assertNotify(NotificationType.INFORMATION, "Already up-to-date<br/>Delete master2");
  }

  @Test
  public void merge_one_simple_and_other_up_to_date() throws Exception {
    branch(myUltimate, "master2");
    branch(myContrib, "master2");
    prepareBranchWithCommit("master2", myCommunity);

    doMerge("master2");

    assertNotify(NotificationType.INFORMATION, "Merged master2 to master<br/>Delete master2");
    assertFile(myCommunity, "unmerged_branch_file", "content");
    assertNull(myUltimate.getRoot().findChild("unmerged_branch_file"));
  }

  @Test
  public void merge_with_unmerged_files_in_first_repo_should_show_notification() throws Exception {
    prepareBranchWithCommit("feature");
    GitTestScenarioGenerator.prepareUnmergedFiles(myUltimate);
    doMerge("feature");
    assertNotify(NotificationType.ERROR, unmergedFilesErrorNotificationDescription("merge"));
  }
  
  @Test
  public void merge_with_unmerged_files_in_second_repo_should_propose_to_rollback() throws Exception {
    prepareBranchWithCommit("feature");
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    doMerge("feature");
    assertMessage(unmergedFilesErrorTitle("merge"));
  }
  
  @Test
  public void rollback_merge_should_reset_merge() throws Exception {
    prepareBranchWithCommit("feature");
    String ultimateTip = tip(myUltimate);
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    myMessageManager.nextAnswer(Messages.OK);
    doMerge("feature");
    assertMessage(unmergedFilesErrorTitle("merge"));
    assertBranch("master");
    assertEquals(tip(myUltimate), ultimateTip);
  }

  @Test
  public void deny_rollback_merge_should_leave_as_is() throws Exception {
    prepareBranchWithCommit("feature");
    String ultimateTip = tip(myUltimate);
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    myMessageManager.nextAnswer(Messages.CANCEL);
    doMerge("feature");
    assertMessage(unmergedFilesErrorTitle("merge"));
    assertBranch("master");
    assertFalse(tip(myUltimate).equals(ultimateTip));
  }

  @Test
  public void merge_with_untracked_files_overwritten_by_merge_in_first_repo_should_show_notification() throws Exception {
    test_untracked_files_overwritten_by_in_first_repo(false);
  }

  @Test
  public void merge_with_untracked_files_overwritten_by_merge_in_second_repo_should_show_rollback_proposal_with_file_list() throws Exception {
    test_checkout_with_untracked_files_overwritten_by_in_second_repo(false);
  }

  @Test
  public void merge_with_local_changes_overwritten_by_merge_should_show_smart_checkout_dialog() throws Exception {
    test_operation_with_local_changes_overwritten_by_should_show_smart_checkout_dialog(false);
  }

  @Test
  public void agree_to_smart_merge_should_smart_merge() throws Exception {
    prepare_agree_to_smart_operation(false);
    for (GitRepository repository : myRepositories) {
      assertEquals(read(repository, "local.txt"), "master\ninitial content\nfeature content\n");
    }
  }

  @Test
  public void deny_to_smart_merge_in_first_repo_should_show_notification() throws Exception {
    test_deny_to_smart_operation_in_first_repo_should_show_notification(false);
  }

  @Test
  public void deny_to_smart_merge_in_second_repo_should_show_rollback_proposal() throws Exception {
    test_deny_to_smart_operation_in_second_repo_should_show_rollback_proposal(false);
  }

  private void registerNotFullyMergedDialog(final int answer) {
    myDialogManager.registerDialogHandler(GitBranchIsNotFullyMergedDialog.class, new TestDialogHandler<GitBranchIsNotFullyMergedDialog>() {
      @Override
      public int handleDialog(GitBranchIsNotFullyMergedDialog dialog) {
        return answer;
      }
    });
  }

  private void prepareBranchWithCommit(String branch, GitRepository... repositories) throws IOException {
    if (repositories.length == 0) {
      repositories = new GitRepository[] { myUltimate, myCommunity, myContrib };
    }
    for (GitRepository repository : repositories) {
      checkout(repository, "-b", branch);
      createAddCommit(repository, "unmerged_branch_file");
      checkout(repository, "master");
      refresh(repository);
    }
  }

  private void doCheckoutNewBranch() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    callPrivateBranchOperationsProcessorMethod("doCheckoutNewBranch", NEW_BRANCH);
  }

  private void doMerge(String branch) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    GitBranchOperationsProcessor processor = new GitBranchOperationsProcessor(myProject, myRepositories, myCommunity);
    Method method = GitBranchOperationsProcessor.class.getDeclaredMethod("doMerge", String.class, Boolean.TYPE, ProgressIndicator.class);
    method.setAccessible(true);
    method.invoke(processor, branch, true, new EmptyProgressIndicator());

    // sync refresh is needed, because the refresh inside GitMergeOperation is asynchronous.
    for (GitRepository repository : myRepositories) {
      repository.getRoot().refresh(false, true);
    }
  }

  private void doCheckoutOrMerge(boolean checkout, String branch) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    if (checkout) {
      doCheckout(branch, null);
    }
    else {
      doMerge(branch);
    }
  }

  private static String unmergedFilesErrorNotificationDescription(String operation)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method method = GitBranchOperation.class.getDeclaredMethod("unmergedFilesErrorNotificationDescription", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, operation);
  }

  private static String unmergedFilesErrorTitle(String operation)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method method = GitBranchOperation.class.getDeclaredMethod("unmergedFilesErrorTitle", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, operation);
  }

  private void callPrivateBranchOperationsProcessorMethod(String methodName, String branchName) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    // call private doCheckoutNewBranch instead of public checkoutNewBranch to avoid dealing with background process creation
    // same for other branch operations
    GitBranchOperationsProcessor processor = new GitBranchOperationsProcessor(myProject, myRepositories, myCommunity);
    Method method = GitBranchOperationsProcessor.class.getDeclaredMethod(methodName, String.class, ProgressIndicator.class);
    method.setAccessible(true);
    method.invoke(processor, branchName, new EmptyProgressIndicator());
  }

  private void doDeleteBranch(@NotNull String branchName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    callPrivateBranchOperationsProcessorMethod("doDelete", branchName);
  }
  
  private void doCheckout(@NotNull String branchName, @Nullable String newBranch) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    GitBranchOperationsProcessor processor = new GitBranchOperationsProcessor(myProject, myRepositories, myCommunity);
    Method doCheckout = GitBranchOperationsProcessor.class.getDeclaredMethod("doCheckout", ProgressIndicator.class, String.class, String.class);
    doCheckout.setAccessible(true);
    doCheckout.invoke(processor, new EmptyProgressIndicator(), branchName, newBranch);
  }

  private void assertBranch(String branch) throws IOException {
    for (GitRepository repository : myRepositories) {
      assertBranch(repository, branch);
    }
  }

  private static void assertBranch(GitRepository repository, String branchName) throws IOException {
    String currentBranch = currentBranch(repository);
    assertNotNull(currentBranch);
    assertEquals(currentBranch, branchName, "Expected " + branchName + " in [" + getShortRepositoryName(repository) + "]");
  }

  private void assertNotify(NotificationType type, String content) {
    assertNotify(type, null, content);
  }

  private void assertNotify(NotificationType type, @Nullable String title, String content) {
    Notification notification = myNotificationManager.getLastNotification();
    assertNotNull(notification);
    assertEquals(stripHtmlAndBreaks(notification.getContent()), stripHtmlAndBreaks(content));
    assertEquals(notification.getType(), type);
    if (title != null) {
      assertEquals(stripHtmlAndBreaks(notification.getTitle()), stripHtmlAndBreaks(title));
    }
  }

  @NotNull
  private static String stripHtmlAndBreaks(@NotNull String text) {
    return StringUtil.stripHtml(text, true).replace("\n", "");
  }

  private void assertMessage(String title) {
    assertMessage(title, null, null, null);
  }
  
  private void assertMessage(@Nullable String title, @Nullable String description, @Nullable String yesButton, @Nullable String noButton) {
    TestMessageManager.Message message = myMessageManager.getLastMessage();
    assertNotNull(message);
    if (title != null) {
      assertEquals(stripHtmlAndBreaks(message.getTitle()), stripHtmlAndBreaks(title));
    }
    if (description != null) {
      assertEquals(stripHtmlAndBreaks(message.getDescription()), stripHtmlAndBreaks(description));
    }
    if (yesButton != null) {
      assertEquals(message.getYesText(), yesButton);
    }
    if (noButton != null) {
      assertEquals(message.getNoText(), noButton);
    }
  }

}
