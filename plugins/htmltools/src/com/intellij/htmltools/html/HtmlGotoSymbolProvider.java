package com.intellij.htmltools.html;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Objects;
import java.util.Optional;

final class HtmlGotoSymbolProvider implements ChooseByNameContributorEx {
  @Override
  public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    FileBasedIndex.getInstance().processAllKeys(HtmlTagIdIndex.INDEX,
                                                (name) -> processor.process(name) && processor.process("#" + name),
                                                scope, filter);
  }

  @Override
  public void processElementsWithName(@NotNull String name,
                                      @NotNull Processor<? super NavigationItem> processor,
                                      @NotNull FindSymbolParameters parameters) {
    String idName = StringUtil.trimStart(name, "#");
    FileBasedIndex.getInstance().processValues(HtmlTagIdIndex.INDEX, idName, null, (file, value) -> {
      var navigationItem = new OffsetNavigationItem(parameters.getProject(), file, value, name,
                                                    getLocationString(parameters.getProject(), file));
      return processor.process(navigationItem);
    }, parameters.getSearchScope(), parameters.getIdFilter());
  }

  private static @Nullable String getLocationString(@NotNull Project project, @NotNull VirtualFile file) {
    return Optional.ofNullable(ProjectUtil.guessProjectDir(project))
      .map(projectDir -> VfsUtilCore.getRelativePath(file, projectDir, File.separatorChar))
      .map(path -> "(" + path + ")")
      .orElse(null);
  }

  private static final class OffsetNavigationItem implements NavigationItem {
    private final VirtualFile myFile;
    private final Integer myValue;
    private final String myName;
    private final String myLocationString;
    private final Project myProject;

    private OffsetNavigationItem(Project project, VirtualFile file, Integer value, String name, String locationString) {
      myProject = project;
      myFile = file;
      myValue = value;
      myName = name;
      myLocationString = locationString;
    }

    @Override
    public void navigate(boolean requestFocus) {
      new OpenFileDescriptor(myProject, myFile, myValue).navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return true;
    }

    @Override
    public boolean canNavigateToSource() {
      return true;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public ItemPresentation getPresentation() {
      return new ItemPresentation() {
        @Override
        public String getPresentableText() {
          return myName;
        }

        @Override
        public @Nullable String getLocationString() {
          return myLocationString;
        }

        @Override
        public Icon getIcon(boolean unused) {
          return AllIcons.Xml.Html_id;
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      OffsetNavigationItem item = (OffsetNavigationItem)o;
      return myFile.equals(item.myFile) &&
             myValue.equals(item.myValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFile, myValue);
    }
  }
}
