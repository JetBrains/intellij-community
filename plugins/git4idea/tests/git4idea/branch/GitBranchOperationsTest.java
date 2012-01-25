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
import git4idea.GitBranch;
import git4idea.GitVcs;
import git4idea.repo.GitRepository;
import git4idea.test.GitTestScenarioGenerator;
import git4idea.test.GitTestUtil;
import git4idea.test.TestMessageManager;
import git4idea.test.TestNotificationManager;
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
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.test.GitExec.*;
import static org.testng.Assert.*;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchOperationsTest extends AbstractVcsTestCase  {

  private static final String NEW_BRANCH = "new_branch";
  private static final String MASTER = "master";

  private Collection<GitRepository> myRepositories;
  private GitRepository myUltimate;
  private GitRepository myCommunity;
  private GitRepository myContrib;

  private TestMessageManager myMessageManager;
  private TestNotificationManager myNotificationManager;
  private TestDialogManager myDialogManager;
  
  private TempDirTestFixture myTempDirFixture;

  @BeforeMethod
  public void setup() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    myTempDirFixture = fixtureFactory.createTempDirTestFixture();
    myTempDirFixture.setUp();
    
    final File projectDir = new File(myTempDirFixture.getTempDirPath(), "ultimate");
    assertTrue(projectDir.mkdir());
    
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          initProject(projectDir);
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

    myDialogManager = GitTestUtil.registerDialogManager(myProject);
    myNotificationManager = GitTestUtil.registerNotificationManager(myProject);
    myMessageManager = GitTestUtil.registerMessageManager(myProject);
    
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
    assertNotify(NotificationType.ERROR, GitBranchOperation.UNMERGED_FILES_ERROR_NOTIFICATION_DESCRIPTION);
  }
  
  @Test
  public void create_new_branch_with_unmerged_files_in_second_repo_should_propose_to_rollback() throws Exception {
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity, myContrib);
    doCheckoutNewBranch();
    assertMessage(GitBranchOperation.UNMERGED_FILES_ERROR_TITLE);
  }

  @Test
  public void rollback_create_new_branch_should_delete_branch() throws Exception {
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity, myContrib);
    myMessageManager.nextAnswer(Messages.OK);
    doCheckoutNewBranch();
    assertMessage(GitBranchOperation.UNMERGED_FILES_ERROR_TITLE);
    assertBranch("master");
    assertTrue(!branch(myUltimate).contains(NEW_BRANCH));
  }

  @Test
  public void deny_rollback_create_new_branch() throws Exception {
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity, myContrib);
    myMessageManager.nextAnswer(Messages.CANCEL);
    doCheckoutNewBranch();
    assertMessage(GitBranchOperation.UNMERGED_FILES_ERROR_TITLE);

    assertBranch(myUltimate, NEW_BRANCH);
    assertBranch(myCommunity, MASTER);
    assertBranch(myContrib, MASTER);
  }
  
  @Test
  public void checkout_branch_without_problems() throws Exception {
    prepareBranchForSimpleCheckout();
    doCheckout("feature", null);
    assertNotify(NotificationType.INFORMATION, "Checked out feature");
  }
  
  @Test
  public void checkout_branch_with_unmerged_files_in_first_repo_should_show_notification() throws Exception {
    prepareBranchForSimpleCheckout();
    GitTestScenarioGenerator.prepareUnmergedFiles(myUltimate);
    doCheckout("feature", null);
    assertNotify(NotificationType.ERROR, GitBranchOperation.UNMERGED_FILES_ERROR_NOTIFICATION_DESCRIPTION);
  }
  
  @Test
  public void checkout_branch_with_unmerged_file_in_second_repo_should_propose_to_rollback() throws Exception {
    prepareBranchForSimpleCheckout();
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    doCheckout("feature", null);
    assertMessage(GitBranchOperation.UNMERGED_FILES_ERROR_TITLE);
  }

  @Test
  public void rollback_checkout_should_return_to_previous_branch() throws Exception {
    prepareBranchForSimpleCheckout();
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    myMessageManager.nextAnswer(Messages.OK);
    doCheckout("feature", null);
    assertMessage(GitBranchOperation.UNMERGED_FILES_ERROR_TITLE);
    assertBranch("master");
  }

  @Test
  public void deny_rollback_checkout_should_do_nothing() throws Exception {
    prepareBranchForSimpleCheckout();
    GitTestScenarioGenerator.prepareUnmergedFiles(myCommunity);
    myMessageManager.nextAnswer(Messages.CANCEL);
    doCheckout("feature", null);
    assertMessage(GitBranchOperation.UNMERGED_FILES_ERROR_TITLE);
    assertBranch(myUltimate, "feature");
    assertBranch(myCommunity, "master");
    assertBranch(myContrib, "master");
  }

  @Test
  public void checkout_branch_with_untracked_files_overwritten_by_checkout_in_first_repo_should_show_notification() throws Exception {
    prepareUntrackedFilesAndBranchWithSameTrackedFiles(myUltimate);
    branch(myCommunity, "feature");
    branch(myContrib, "feature");

    doCheckout("feature", null);
    assertNotify(NotificationType.ERROR, UntrackedFilesNotifier.createUntrackedFilesOverwrittenDescription("checkout", false));
  }

  @Test
  public void checkout_branch_with_untracked_files_overwritten_by_checkout_in_second_repo_should_show_rollback_proposal_with_file_list() throws Exception {
    prepareUntrackedFilesAndBranchWithSameTrackedFiles(myCommunity);
    branch(myUltimate, "feature");
    branch(myContrib, "feature");

    Class gitCheckoutOperationClass = Class.forName("git4idea.branch.GitCheckoutOperation");
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

    doCheckout("feature", null);
    assertTrue(dialogShown.get());
  }

  private static void prepareUntrackedFilesAndBranchWithSameTrackedFiles(GitRepository repository) throws IOException {
    checkout(repository, "-b", "feature");
    createAddCommit(repository, "untracked.txt");
    checkout(repository, "master");
    create(repository, "untracked.txt");
  }

  private void prepareBranchForSimpleCheckout() throws IOException {
    for (GitRepository repository : myRepositories) {
      checkout(repository, "-b", "feature");
      createAddCommit(repository, "feature_file.txt");
      checkout(repository, "master");
    }
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
    prepareUnmergedBranch(myUltimate, myCommunity, myContrib);

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
    prepareUnmergedBranch(myUltimate, myCommunity, myContrib);
    registerNotFullyMergedDialog(DialogWrapper.OK_EXIT_CODE);
    doDeleteBranch("unmerged_branch");
    for (GitRepository repository : myRepositories) {
      assertTrue(!branch(repository).contains("unmerged_branch"));
    }
  }
  
  @Test
  public void cancel_in_unmerged_branch_dialog_in_first_repository_should_show_notification() throws Exception {
    prepareUnmergedBranch(myUltimate, myContrib);
    branch(myCommunity, "unmerged_branch");

    registerNotFullyMergedDialog(DialogWrapper.CANCEL_EXIT_CODE);
    doDeleteBranch("unmerged_branch");
    assertNotify(NotificationType.ERROR, "Branch unmerged_branch wasn't deleted", "This branch is not fully merged to master");
  }
  
  @Test
  public void cancel_in_unmerged_branch_dialog_in_not_first_repository_should_show_rollback_proposal() throws Exception {
    branch(myUltimate, "unmerged_branch");
    prepareUnmergedBranch(myCommunity, myContrib);

    registerNotFullyMergedDialog(DialogWrapper.CANCEL_EXIT_CODE);
    doDeleteBranch("unmerged_branch");
    assertMessage(String.format("Branch %s wasn't deleted", "unmerged_branch"));
  }
  
  @Test
  public void rollback_delete_branch_should_recreate_branches() throws Exception {
    branch(myUltimate, "unmerged_branch");
    prepareUnmergedBranch(myCommunity);
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
    prepareUnmergedBranch(myCommunity);
    branch(myContrib, "unmerged_branch");
    
    registerNotFullyMergedDialog(DialogWrapper.CANCEL_EXIT_CODE);
    myMessageManager.nextAnswer(Messages.CANCEL);
    doDeleteBranch("unmerged_branch");

    assertTrue(branch(myCommunity).contains("unmerged_branch"));
    assertTrue(branch(myContrib).contains("unmerged_branch"));
    assertTrue(!branch(myUltimate).contains("unmerged_branch"));
  }

  private void registerNotFullyMergedDialog(final int answer) {
    myDialogManager.registerDialogHandler(GitBranchIsNotFullyMergedDialog.class, new TestDialogHandler<GitBranchIsNotFullyMergedDialog>() {
      @Override
      public int handleDialog(GitBranchIsNotFullyMergedDialog dialog) {
        return answer;
      }
    });
  }

  private static void prepareUnmergedBranch(GitRepository... repositories) throws IOException {
    for (GitRepository repository : repositories) {
      checkout(repository, "-b", "unmerged_branch");
      createAddCommit(repository, "unmerged_branch_file");
      checkout(repository, "master");
      refresh(repository);
    }
  }

  private void doCheckoutNewBranch() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    callPrivateBranchOperationsProcessorMethod("doCheckoutNewBranch", NEW_BRANCH);
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

  private void assertBranch(String branch) {
    for (GitRepository repository : myRepositories) {
      assertBranch(repository, branch);
    }
  }

  private static void assertBranch(GitRepository repository, String branchName) {
    GitBranch currentBranch = repository.getCurrentBranch();
    assertNotNull(currentBranch);
    assertEquals(currentBranch.getName(), branchName);
  }

  private void assertNotify(NotificationType type, String content) {
    assertNotify(type, null, content);
  }

  private void assertNotify(NotificationType type, @Nullable String title, String content) {
    Notification notification = myNotificationManager.getLastNotification();
    assertNotNull(notification);
    assertEquals(stripHtml(notification.getContent()), stripHtml(content));
    assertEquals(notification.getType(), type);
    if (title != null) {
      assertEquals(stripHtml(notification.getTitle()), stripHtml(title));
    }
  }

  private static String stripHtml(String text) {
    return StringUtil.stripHtml(text, true);
  }

  private void assertMessage(String title) {
    assertMessage(title, null, null, null);
  }
  
  private void assertMessage(@Nullable String title, @Nullable String description, @Nullable String yesButton, @Nullable String noButton) {
    TestMessageManager.Message message = myMessageManager.getLastMessage();
    assertNotNull(message);
    if (title != null) {
      assertEquals(message.getTitle(), title);
    }
    if (description != null) {
      assertEquals(message.getDescription(), description);
    }
    if (yesButton != null) {
      assertEquals(message.getYesText(), yesButton);
    }
    if (noButton != null) {
      assertEquals(message.getNoText(), noButton);
    }
  }

}
