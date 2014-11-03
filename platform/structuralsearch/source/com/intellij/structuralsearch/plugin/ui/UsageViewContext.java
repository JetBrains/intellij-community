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
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
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
  private final Runnable mySearchStarter;
  private UsageView myUsageView;
  protected final Configuration myConfiguration;
  private Set<Usage> myExcludedSet;

  protected UsageViewContext(Configuration configuration, SearchContext searchContext, Runnable searchStarter) {
    myConfiguration = configuration;
    mySearchContext = searchContext;
    mySearchStarter = searchStarter;
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

  public ConfigurableUsageTarget getTarget() {
    return new MyUsageTarget();
  }

  public void configure(@NotNull UsageViewPresentation presentation) {
    final String pattern = myConfiguration.getMatchOptions().getSearchPattern();
    final String scopeText = myConfiguration.getMatchOptions().getScope().getDisplayName();
    presentation.setScopeText(scopeText);
    final String usagesString = SSRBundle.message("occurrences.of", pattern);
    presentation.setUsagesString(usagesString);
    presentation.setTabText(StringUtil.shortenTextWithEllipsis(usagesString, 60, 0, false));
    presentation.setUsagesWord(SSRBundle.message("occurrence"));
    presentation.setCodeUsagesString(SSRBundle.message("found.occurrences", scopeText));
    presentation.setTargetsNodeText(SSRBundle.message("targets.node.text"));
    presentation.setCodeUsages(false);
  }

  protected void configureActions() {}

  private class MyUsageTarget implements ConfigurableUsageTarget, ItemPresentation {

    @NotNull
    @Override
    public String getPresentableText() {
      return myConfiguration.getMatchOptions().getSearchPattern();
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
      mySearchStarter.run();
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
      return ActionManager.getInstance().getKeyboardShortcut(myConfiguration instanceof ReplaceConfiguration
                                                             ? "StructuralSearchPlugin.StructuralReplaceAction"
                                                             : "StructuralSearchPlugin.StructuralSearchAction");
    }

    @NotNull
    @Override
    public String getLongDescriptiveName() {
      final MatchOptions matchOptions = myConfiguration.getMatchOptions();
      final String pattern = matchOptions.getSearchPattern();
      final String scope = matchOptions.getScope().getDisplayName();
      final String result;
      if (myConfiguration instanceof ReplaceConfiguration) {
        final ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)myConfiguration;
        final String replacement = replaceConfiguration.getOptions().getReplacement();
        result = SSRBundle.message("replace.occurrences.of.0.with.1.in.2", pattern, replacement, scope);
      }
      else {
        result = SSRBundle.message("occurrences.of.0.in.1", pattern, scope);
      }
      return StringUtil.shortenTextWithEllipsis(result, 150, 0, true);
    }
  }
}
