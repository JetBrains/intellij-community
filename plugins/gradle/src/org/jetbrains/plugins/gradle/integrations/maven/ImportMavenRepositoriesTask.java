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
package org.jetbrains.plugins.gradle.integrations.maven;

import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.indices.MavenRepositoriesConfigurable;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import javax.swing.event.HyperlinkEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 10/29/13
 */
public class ImportMavenRepositoriesTask implements Runnable {

  private static final String UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP = "Unindexed maven repositories gradle detection";
  @NotNull
  private final MavenRemoteRepository mavenCentralRemoteRepository;

  private final Project myProject;

  public ImportMavenRepositoriesTask(Project project) {
    myProject = project;
    mavenCentralRemoteRepository = new MavenRemoteRepository("central", null, "http://repo1.maven.org/maven2/", null, null, null);
  }

  @Override
  public void run() {
    if(myProject.isDisposed()) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    final List<PsiFile> psiFileList = ContainerUtil.newArrayList();

    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) continue;

      final String modulePath = ExternalSystemApiUtil.getExternalProjectPath(module);
      if (modulePath == null) continue;

      String buildScript = FileUtil.findFileInProvidedPath(modulePath, GradleConstants.DEFAULT_SCRIPT_NAME);
      if (StringUtil.isEmpty(buildScript)) continue;

      VirtualFile virtualFile =
        localFileSystem.refreshAndFindFileByPath(buildScript);
      if (virtualFile == null) continue;

      final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      if (psiFile == null) continue;
      psiFileList.add(psiFile);
    }

    final PsiFile[] psiFiles = ArrayUtil.toObjectArray(psiFileList, PsiFile.class);

    final Set<MavenRemoteRepository> mavenRemoteRepositories = new ReadAction<Set<MavenRemoteRepository>>() {
      @Override
      protected void run(@NotNull Result<Set<MavenRemoteRepository>> result) throws Throwable {
        Set<MavenRemoteRepository> myRemoteRepositories = ContainerUtil.newHashSet();
        for (PsiFile psiFile : psiFiles) {
          List<GrClosableBlock> repositoriesBlocks = ContainerUtil.newArrayList();
          repositoriesBlocks.addAll(findClosableBlocks(psiFile, "repositories"));

          for (GrClosableBlock closableBlock : findClosableBlocks(psiFile, "buildscript", "subprojects", "allprojects", "project", "configure")) {
            repositoriesBlocks.addAll(findClosableBlocks(closableBlock, "repositories"));
          }

          for (GrClosableBlock repositoriesBlock : repositoriesBlocks) {
            myRemoteRepositories.addAll(findMavenRemoteRepositories(repositoriesBlock));
          }
        }

        result.setResult(myRemoteRepositories);
      }
    }.execute().getResultObject();

    if (mavenRemoteRepositories == null || mavenRemoteRepositories.isEmpty()) return;

    // register imported maven repository URLs but do not force to download the index
    // the index can be downloaded and/or updated later using Maven Configuration UI (Settings -> Build, Execution, Deployment -> Build tools -> Maven -> Repositories)
    MavenRepositoriesHolder.getInstance(myProject).update(mavenRemoteRepositories);
    MavenProjectIndicesManager.getInstance(myProject).scheduleUpdateIndicesList(new Consumer<List<MavenIndex>>() {
      @Override
      public void consume(List<MavenIndex> indexes) {
        if (myProject.isDisposed()) return;

        final List<String> repositoriesWithEmptyIndex = ContainerUtil.mapNotNull(indexes, new Function<MavenIndex, String>() {
          @Override
          public String fun(MavenIndex index) {
            return index.getUpdateTimestamp() == -1 &&
                   MavenRepositoriesHolder.getInstance(myProject).contains(index.getRepositoryPathOrUrl())
                   ? index.getRepositoryPathOrUrl() : null;
          }
        });

        if (!repositoriesWithEmptyIndex.isEmpty()) {
          final NotificationData notificationData = new NotificationData(
            GradleBundle.message("gradle.integrations.maven.notification.not_updated_repository.title"),
            "\n<br>" + GradleBundle.message("gradle.integrations.maven.notification.not_updated_repository.text", StringUtil.join(repositoriesWithEmptyIndex, "<br>")),
            NotificationCategory.WARNING,
            NotificationSource.PROJECT_SYNC);
          notificationData.setBalloonNotification(true);
          notificationData.setBalloonGroup(UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP);
          notificationData.setListener("#open", new NotificationListener.Adapter() {
            @Override
            protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
              ShowSettingsUtil.getInstance().showSettingsDialog(myProject, MavenRepositoriesConfigurable.class);
            }
          });

          notificationData.setListener("#disable", new NotificationListener.Adapter() {
            @Override
            protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
              final int result =
                Messages.showYesNoDialog(myProject,
                                         "Notification will be disabled for all projects.\n\n" +
                                         "Settings | Appearance & Behavior | Notifications | " +
                                         UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP +
                                         "\ncan be used to configure the notification.",
                                         "Unindexed Maven Repositories Gradle Detection",
                                         "Disable Notification", CommonBundle.getCancelButtonText(),
                                         Messages.getWarningIcon());
              if (result == Messages.YES) {
                NotificationsConfigurationImpl.getInstanceImpl().changeSettings(UNINDEXED_MAVEN_REPOSITORIES_NOTIFICATION_GROUP,
                                                                                NotificationDisplayType.NONE, false, false);

                notification.hideBalloon();
              }
            }
          });

          ExternalSystemNotificationManager.getInstance(myProject).showNotification(GradleConstants.SYSTEM_ID, notificationData);
        }
      }
    });
  }

  @NotNull
  private static Collection<? extends GrClosableBlock> findClosableBlocks(@NotNull final PsiElement element,
                                                                          @NotNull final String... blockNames) {
    List<GrMethodCall> methodCalls = PsiTreeUtil.getChildrenOfTypeAsList(element, GrMethodCall.class);
    return ContainerUtil.mapNotNull(methodCalls, new Function<GrMethodCall, GrClosableBlock>() {
      @Override
      public GrClosableBlock fun(GrMethodCall call) {
        if (call == null || call.getClosureArguments().length != 1) return null;

        GrExpression expression = call.getInvokedExpression();
        return ArrayUtil.contains(expression.getText(), blockNames) ? call.getClosureArguments()[0] : null;
      }
    });
  }

  @NotNull
  private Collection<? extends MavenRemoteRepository> findMavenRemoteRepositories(@Nullable GrClosableBlock repositoriesBlock) {
    Set<MavenRemoteRepository> myRemoteRepositories = ContainerUtil.newHashSet();
    for (GrMethodCall repo : PsiTreeUtil
      .getChildrenOfTypeAsList(repositoriesBlock, GrMethodCall.class)) {

      final String expressionText = repo.getInvokedExpression().getText();
      if ("mavenCentral".equals(expressionText)) {
        myRemoteRepositories.add(mavenCentralRemoteRepository);
      }
      else if ("mavenRepo".equals(expressionText)) {
        for (GrNamedArgument namedArgument : repo.getNamedArguments()) {
          if ("url".equals(namedArgument.getLabelName())) {
            URI urlArgumentValue = resolveUriFromSimpleExpression(namedArgument.getExpression());
            if (urlArgumentValue != null) {
              String textUri = urlArgumentValue.toString();
              myRemoteRepositories.add(new MavenRemoteRepository(textUri, null, textUri, null, null, null));
            }
            break;
          }
        }
      }
      else if ("maven".equals(expressionText) && repo.getClosureArguments().length > 0) {
        List<GrApplicationStatement> applicationStatementList =
          PsiTreeUtil.getChildrenOfTypeAsList(repo.getClosureArguments()[0], GrApplicationStatement.class);
        if (!applicationStatementList.isEmpty()) {
          GrApplicationStatement statement = applicationStatementList.get(0);
          if (statement == null) continue;
          GrExpression expression = statement.getInvokedExpression();

          if ("url".equals(expression.getText())) {
            URI urlArgumentValue = resolveUriFromSimpleExpression(statement.getExpressionArguments()[0]);
            if (urlArgumentValue != null) {
              String textUri = urlArgumentValue.toString();
              myRemoteRepositories.add(new MavenRemoteRepository(textUri, null, textUri, null, null, null));
            }
          }
        }

        List<GrAssignmentExpression> assignmentExpressionList =
          PsiTreeUtil.getChildrenOfTypeAsList(repo.getClosureArguments()[0], GrAssignmentExpression.class);
        if (!assignmentExpressionList.isEmpty()) {
          GrAssignmentExpression statement = assignmentExpressionList.get(0);
          if (statement == null) continue;
          GrExpression expression = statement.getLValue();

          if ("url".equals(expression.getText())) {
            URI urlArgumentValue = resolveUriFromSimpleExpression(statement.getRValue());
            if (urlArgumentValue != null) {
              String textUri = urlArgumentValue.toString();
              myRemoteRepositories.add(new MavenRemoteRepository(textUri, null, textUri, null, null, null));
            }
          }
        }
      }
    }

    return myRemoteRepositories;
  }

  @Nullable
  private static URI resolveUriFromSimpleExpression(@Nullable GrExpression expression) {
    if (expression == null) return null;

    try {
      if (expression instanceof PsiLiteral) {
        URI uri = new URI(String.valueOf(PsiLiteral.class.cast(expression).getValue()));
        if (uri.getScheme() != null && StringUtil.startsWith(uri.getScheme(), "http")) return uri;
      }
    }
    catch (URISyntaxException ignored) {
      // ignore it
    }

    try {
      PsiReference reference = expression.getReference();
      if (reference == null) return null;
      PsiElement element = reference.resolve();
      if (element instanceof GrVariable) {
        List<GrLiteral> grLiterals = PsiTreeUtil.getChildrenOfTypeAsList(element, GrLiteral.class);
        if (grLiterals.isEmpty()) return null;
        URI uri = new URI(String.valueOf(grLiterals.get(0).getValue()));
        if (uri.getScheme() != null && StringUtil.startsWith("http", uri.getScheme())) return uri;
      }
    }
    catch (URISyntaxException ignored) {
      // ignore it
    }

    return null;
  }
}
