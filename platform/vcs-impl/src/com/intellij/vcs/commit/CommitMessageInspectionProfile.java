// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(name = "CommitMessageInspectionProfile", storages = @Storage("vcs.xml"))
public class CommitMessageInspectionProfile extends InspectionProfileImpl
  implements PersistentStateComponent<CommitMessageInspectionProfile.State> {

  private static final String PROFILE_NAME = "Commit Dialog";

  @NotNull private final Project myProject;

  public CommitMessageInspectionProfile(@NotNull Project project) {
    super(PROFILE_NAME, new CommitMessageInspectionToolSupplier());
    myProject = project;
  }

  @NotNull
  public static CommitMessageInspectionProfile getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CommitMessageInspectionProfile.class);
  }

  public static int getBodyRightMargin(@NotNull Project project) {
    return getInstance(project).getBodyRightMargin();
  }

  public static int getSubjectRightMargin(@NotNull Project project) {
    return getInstance(project).getSubjectRightMargin();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private int getBodyRightMargin() {
    return getTool(BodyLimitInspection.class).RIGHT_MARGIN;
  }

  private int getSubjectRightMargin() {
    return getTool(SubjectLimitInspection.class).RIGHT_MARGIN;
  }

  public <T extends LocalInspectionTool> T getTool(Class<T> aClass) {
    InspectionToolWrapper tool = getInspectionTool(InspectionProfileEntry.getShortName(aClass.getSimpleName()), myProject);
    //noinspection unchecked
    return (T)ObjectUtils.notNull(tool).getTool();
  }

  @Override
  protected boolean forceInitInspectionTools() {
    return true;
  }

  @NotNull
  @Override
  @Transient
  public String getName() {
    return super.getName();
  }

  @NotNull
  @Override
  public State getState() {
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

  @NotNull
  private static Element newProfileElement() {
    return new Element(PROFILE);
  }

  @NotNull
  private static Element newProfileWithVersionElement() {
    Element result = newProfileElement();
    writeVersion(result);
    return result;
  }

  private static class CommitMessageInspectionToolSupplier implements Supplier<List<InspectionToolWrapper>> {
    @NotNull
    @Override
    public List<InspectionToolWrapper> get() {
      return Stream.of(new SubjectBodySeparationInspection(), new SubjectLimitInspection(), new BodyLimitInspection(),
                       new CommitMessageSpellCheckingInspection())
                   .map(LocalInspectionToolWrapper::new)
                   .collect(Collectors.toList());
    }
  }
}
