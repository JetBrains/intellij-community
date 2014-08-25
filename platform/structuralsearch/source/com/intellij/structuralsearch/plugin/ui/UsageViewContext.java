package com.intellij.structuralsearch.plugin.ui;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceCommand;
import com.intellij.usages.ConfigurableUsageTarget;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 9, 2005
 * Time: 2:47:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewContext {
  protected final SearchContext mySearchContext;
  private UsageView myUsageView;
  protected final Configuration myConfiguration;
  private Set<Usage> myExcludedSet;
  private SearchCommand myCommand;

  protected UsageViewContext(SearchContext _searchContext,Configuration _configuration) {
    myConfiguration = _configuration;
    mySearchContext = _searchContext;
  }

  public boolean isExcluded(Usage usage) {
    if (myExcludedSet == null) myExcludedSet = myUsageView.getExcludedUsages();
    return myExcludedSet.contains(usage);
  }

  public UsageView getUsageView() {
    return myUsageView;
  }

  public void setUsageView(final UsageView usageView) {
    myUsageView = usageView;
  }

  public Configuration getConfiguration() {
    return myConfiguration;
  }

  public SearchCommand getCommand() {
    if (myCommand == null) myCommand = createCommand();
    return myCommand;
  }

  protected SearchCommand createCommand() {
    return new SearchCommand(mySearchContext.getProject(), this);
  }

  public ConfigurableUsageTarget getTarget() {
    return new MyUsageTarget();
  }

  public void configure(@NotNull UsageViewPresentation presentation) {
    final String pattern = myConfiguration.getMatchOptions().getSearchPattern();
    final String usagesString = SSRBundle.message("occurrences.of", StringUtil.shortenTextWithEllipsis(pattern, 50, 0, true));
    presentation.setUsagesString(SSRBundle.message("occurrences.of", pattern));
    presentation.setTabText(usagesString);
    presentation.setUsagesWord(SSRBundle.message("occurrence"));
    presentation.setCodeUsagesString(SSRBundle.message("found.occurrences"));
  }

  protected void configureActions() {}

  private class MyUsageTarget implements ConfigurableUsageTarget,ItemPresentation {

    @NotNull
    @Override
    public String getPresentableText() {
      final MatchOptions matchOptions = myConfiguration.getMatchOptions();
      return SSRBundle.message("occurrences.of.0.in.1", matchOptions.getSearchPattern(), matchOptions.getScope().getDisplayName());
    }

    @Override
    public String getLocationString() {
      //noinspection HardCodedStringLiteral
      return "Do Not Know Where";
    }

    @Override
    public Icon getIcon(boolean open) {
      return null;
    }

    @Override
    public void findUsages() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void findUsagesInEditor(@NotNull FileEditor editor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void highlightUsages(@NotNull PsiFile file, @NotNull Editor editor, boolean clearHighlights) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public boolean isReadOnly() {
      return true;
    }

    @Override
    public VirtualFile[] getFiles() {
      return null;
    }

    @Override
    public void update() {
    }

    @Override
    public String getName() {
      //noinspection HardCodedStringLiteral
      return "my name";
    }

    @Override
    public ItemPresentation getPresentation() {
      return this;
    }

    @Override
    public void navigate(boolean requestFocus) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public void showSettings() {
      UIUtil.invokeAction(myConfiguration, mySearchContext);
    }

    @Override
    public KeyboardShortcut getShortcut() {
      return ActionManager.getInstance().getKeyboardShortcut(getCommand() instanceof ReplaceCommand ? "StructuralSearchPlugin.StructuralReplaceAction":"StructuralSearchPlugin.StructuralSearchAction");
    }

    @NotNull
    @Override
    public String getLongDescriptiveName() {
      return getPresentableText();
    }
  }
}
