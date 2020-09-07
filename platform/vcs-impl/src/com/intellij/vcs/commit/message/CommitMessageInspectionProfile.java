// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.util.messages.Topic;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EventListener;
import java.util.List;
import java.util.Objects;

@State(name = "CommitMessageInspectionProfile", storages = @Storage("vcs.xml"))
public class CommitMessageInspectionProfile extends InspectionProfileImpl
  implements PersistentStateComponent<CommitMessageInspectionProfile.State> {

  public static final @NotNull Topic<ProfileListener> TOPIC = Topic.create("commit message inspection changes", ProfileListener.class);

  private static final String PROFILE_NAME = "Commit Dialog"; // NON-NLS
  public static final InspectionProfileImpl DEFAULT =
    new InspectionProfileImpl(PROFILE_NAME, new CommitMessageInspectionToolSupplier(), (InspectionProfileImpl)null);

  private final @NotNull Project myProject;

  public CommitMessageInspectionProfile(@NotNull Project project) {
    super(PROFILE_NAME, new CommitMessageInspectionToolSupplier(), DEFAULT);

    myProject = project;
  }

  public static @NotNull CommitMessageInspectionProfile getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CommitMessageInspectionProfile.class);
  }

  public static @NotNull BodyLimitSettings getBodyLimitSettings(@NotNull Project project) {
    VcsConfiguration configuration = VcsConfiguration.getInstance(project);
    CommitMessageInspectionProfile profile = getInstance(project);

    return new BodyLimitSettings(
      profile.getBodyRightMargin(),
      configuration.USE_COMMIT_MESSAGE_MARGIN && profile.isToolEnabled(BodyLimitInspection.class),
      configuration.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN
    );
  }

  public static int getBodyRightMargin(@NotNull Project project) {
    return getInstance(project).getBodyRightMargin();
  }

  public static int getSubjectRightMargin(@NotNull Project project) {
    return getInstance(project).getSubjectRightMargin();
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  private int getBodyRightMargin() {
    return getTool(BodyLimitInspection.class).RIGHT_MARGIN;
  }

  private int getSubjectRightMargin() {
    return getTool(SubjectLimitInspection.class).RIGHT_MARGIN;
  }

  public @NotNull <T extends LocalInspectionTool> T getTool(@NotNull Class<T> aClass) {
    InspectionToolWrapper<?, ?> tool = getInspectionTool(InspectionProfileEntry.getShortName(aClass.getSimpleName()), myProject);
    //noinspection unchecked
    return (T)Objects.requireNonNull(tool).getTool();
  }

  public <T extends LocalInspectionTool> boolean isToolEnabled(@NotNull Class<T> aClass) {
    ToolsImpl tools = getToolsOrNull(InspectionProfileEntry.getShortName(aClass.getSimpleName()), myProject);
    return tools != null && tools.isEnabled();
  }

  @Override
  protected boolean forceInitInspectionTools() {
    return true;
  }

  @Override
  @Transient
  public @NotNull String getName() {
    return super.getName();
  }

  @Override
  public @NotNull State getState() {
    Element element = newProfileElement();
    writeExternal(element);
    State state = new State();
    state.myProfile = element;
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    readExternal(state.myProfile);
  }

  public static class State {
    @Tag(PROFILE)
    public Element myProfile = newProfileWithVersionElement();
  }

  private static @NotNull Element newProfileElement() {
    return new Element(PROFILE);
  }

  private static @NotNull Element newProfileWithVersionElement() {
    Element result = newProfileElement();
    writeVersion(result);
    return result;
  }

  private static class CommitMessageInspectionToolSupplier extends InspectionToolsSupplier {
    @Override
    public @NotNull List<InspectionToolWrapper<?, ?>> createTools() {
      return Arrays.asList(
        new LocalInspectionToolWrapper(new SubjectBodySeparationInspection()),
        new LocalInspectionToolWrapper(new SubjectLimitInspection()),
        new LocalInspectionToolWrapper(new BodyLimitInspection()),
        new LocalInspectionToolWrapper(new CommitMessageSpellCheckingInspection())
      );
    }
  }

  public interface ProfileListener extends EventListener {
    void profileChanged();
  }
}
