// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.DateFilterComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;

@ApiStatus.Internal
public final class CompositeCommittedChangesProvider implements CommittedChangesProvider<CommittedChangeList, CompositeCommittedChangesProvider.CompositeChangeBrowserSettings> {
  private final Project myProject;
  private final List<AbstractVcs> myBaseVcss;

  public CompositeCommittedChangesProvider(@NotNull Project project, @NotNull Collection<AbstractVcs> vcses) {
    myProject = project;
    myBaseVcss = new ArrayList<>(vcses);
  }

  @Override
  @NotNull
  public CompositeCommittedChangesProvider.CompositeChangeBrowserSettings createDefaultSettings() {
    Map<AbstractVcs, ChangeBrowserSettings> map = new HashMap<>();
    for(AbstractVcs vcs: myBaseVcss) {
      CommittedChangesProvider<?, ?> provider = vcs.getCommittedChangesProvider();
      assert provider != null;
      map.put(vcs, provider.createDefaultSettings());
    }
    return new CompositeChangeBrowserSettings(map);
  }

  @NotNull
  @Override
  public ChangesBrowserSettingsEditor<CompositeCommittedChangesProvider.CompositeChangeBrowserSettings> createFilterUI(boolean showDateFilter) {
    return new CompositeChangesBrowserSettingsEditor();
  }

  @Nullable
  @Override
  public RepositoryLocation getLocationFor(@NotNull FilePath root) {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root);
    if (vcs == null) return null;

    return CommittedChangesCache.getInstance(myProject).getLocationCache().getLocation(vcs, root, false);
  }

  @Override
  @Nullable
  public VcsCommittedListsZipper getZipper() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<CommittedChangeList> getCommittedChanges(CompositeCommittedChangesProvider.CompositeChangeBrowserSettings settings,
                                                       RepositoryLocation location,
                                                       int maxCount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void loadCommittedChanges(CompositeChangeBrowserSettings settings,
                                   @NotNull RepositoryLocation location,
                                   int maxCount,
                                   @NotNull AsynchConsumer<? super CommittedChangeList> consumer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ChangeListColumn<?> @NotNull [] getColumns() {
    Set<ChangeListColumn<?>> columns = new LinkedHashSet<>();
    for(AbstractVcs vcs: myBaseVcss) {
      CommittedChangesProvider<?, ?> provider = vcs.getCommittedChangesProvider();
      assert provider != null;
      ChangeListColumn[] providerColumns = provider.getColumns();
      for(ChangeListColumn col: providerColumns) {
        if (col == ChangeListColumn.DATE || col == ChangeListColumn.DESCRIPTION || col == ChangeListColumn.NAME ||
            col instanceof ChangeListColumn.ChangeListNumberColumn) {
          columns.add(col);
        }
      }
    }
    return columns.toArray(new ChangeListColumn[0]);
  }

  @Override
  @Nullable
  public VcsCommittedViewAuxiliary createActions(@NotNull DecoratorManager manager, @Nullable RepositoryLocation location) {
    JTabbedPane tabbedPane = null;
    List<AnAction> actions = null;
    List<AnAction> toolbarActions = null;

    final List<Runnable> calledOnDispose = new ArrayList<>();
    for (AbstractVcs baseVcs : myBaseVcss) {
      CommittedChangesProvider<?, ?> provider = baseVcs.getCommittedChangesProvider();
      if (provider != null) {
        VcsCommittedViewAuxiliary auxiliary = provider.createActions(manager, location);
        if (auxiliary != null) {
          if (tabbedPane == null) {
            tabbedPane = new JBTabbedPane();
            actions = new ArrayList<>();
            toolbarActions = new ArrayList<>();
          }
          actions.addAll(auxiliary.getPopupActions());
          toolbarActions.addAll(auxiliary.getToolbarActions());
          calledOnDispose.add(auxiliary.getCalledOnViewDispose());
        }
      }
    }
    if (tabbedPane != null) {
      final JPanel panel = new JPanel();
      panel.add(tabbedPane);
      return new VcsCommittedViewAuxiliary(actions, () -> {
        for (Runnable runnable : calledOnDispose) {
          runnable.run();
        }
      }, toolbarActions);
    }
    return null;
  }

  @Override
  public int getUnlimitedCountValue() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public Pair<CommittedChangeList, FilePath> getOneList(VirtualFile file, VcsRevisionNumber number) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public RepositoryLocation getForNonLocal(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  public static final class CompositeChangeBrowserSettings extends ChangeBrowserSettings {
    private final Map<AbstractVcs, ChangeBrowserSettings> myMap;
    private final Set<AbstractVcs> myEnabledVcs = new HashSet<>();

    public CompositeChangeBrowserSettings(final Map<AbstractVcs, ChangeBrowserSettings> map) {
      myMap = map;
      myEnabledVcs.addAll(map.keySet());
    }

    public void put(final AbstractVcs vcs, final ChangeBrowserSettings settings) {
      myMap.put(vcs, settings);
    }

    public ChangeBrowserSettings get(final AbstractVcs vcs) {
      return myMap.get(vcs);
    }

    public void setEnabledVcss(Collection<? extends AbstractVcs> vcss) {
      myEnabledVcs.clear();
      myEnabledVcs.addAll(vcss);
    }

    public Collection<AbstractVcs> getEnabledVcss() {
      return myEnabledVcs;
    }
  }

  private class CompositeChangesBrowserSettingsEditor implements ChangesBrowserSettingsEditor<CompositeChangeBrowserSettings> {
    @NotNull private final JPanel myCompositePanel;
    private final DateFilterComponent myDateFilter;
    private CompositeChangeBrowserSettings mySettings;
    private final Map<AbstractVcs, ChangesBrowserSettingsEditor<?>> myEditors = new HashMap<>();
    private final Map<AbstractVcs, JCheckBox> myEnabledCheckboxes = new HashMap<>();

    CompositeChangesBrowserSettingsEditor() {
      myCompositePanel = new JPanel();
      myCompositePanel.setLayout(new BoxLayout(myCompositePanel, BoxLayout.Y_AXIS));
      myDateFilter = new DateFilterComponent().withBorder(IdeBorderFactory.createTitledBorder(VcsBundle.message("border.changes.filter.date.filter")));
      myCompositePanel.add(myDateFilter.getPanel());
      for(AbstractVcs vcs: myBaseVcss) {
        CommittedChangesProvider<?, ?> provider = vcs.getCommittedChangesProvider();
        assert provider != null;
        ChangesBrowserSettingsEditor<?> editor = provider.createFilterUI(false);
        myEditors.put(vcs, editor);

        JPanel wrapperPane = new JPanel(new BorderLayout());
        wrapperPane.setBorder(IdeBorderFactory.createTitledBorder(vcs.getDisplayName()));
        final JCheckBox checkBox = new JCheckBox(VcsBundle.message("composite.change.provider.include.vcs.checkbox", vcs.getDisplayName()), true);
        checkBox.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            updateVcsEnabled(checkBox, editor);
          }
        });
        wrapperPane.add(checkBox, BorderLayout.NORTH);
        myEnabledCheckboxes.put(vcs, checkBox);
        wrapperPane.add(editor.getComponent(), BorderLayout.CENTER);
        myCompositePanel.add(wrapperPane);
      }
    }

    private static void updateVcsEnabled(@NotNull JCheckBox checkBox, @NotNull ChangesBrowserSettingsEditor<?> editor) {
      UIUtil.setEnabled(editor.getComponent(), checkBox.isSelected(), true);
      if (checkBox.isSelected()) {
        editor.updateEnabledControls();
      }
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myCompositePanel;
    }

    @NotNull
    @Override
    public CompositeChangeBrowserSettings getSettings() {
      Set<AbstractVcs> enabledVcss = new HashSet<>();
      for(AbstractVcs vcs: myEditors.keySet()) {
        ChangeBrowserSettings settings = myEditors.get(vcs).getSettings();
        myDateFilter.saveValues(settings);
        mySettings.put(vcs, settings);
        if (myEnabledCheckboxes.get(vcs).isSelected()) {
          enabledVcss.add(vcs);
        }
      }
      mySettings.setEnabledVcss(enabledVcss);
      return mySettings;
    }

    @Override
    public void setSettings(@NotNull CompositeChangeBrowserSettings settings) {
      mySettings = settings;
      boolean dateFilterInitialized = false;
      for (AbstractVcs vcs : myEditors.keySet()) {
        ChangeBrowserSettings vcsSettings = mySettings.get(vcs);
        ChangesBrowserSettingsEditor editor = myEditors.get(vcs);
        //noinspection unchecked
        editor.setSettings(vcsSettings);
        if (!dateFilterInitialized) {
          myDateFilter.initValues(vcsSettings);
          dateFilterInitialized = true;
        }
        final JCheckBox checkBox = myEnabledCheckboxes.get(vcs);
        checkBox.setSelected(settings.getEnabledVcss().contains(vcs));
        updateVcsEnabled(checkBox, editor);
      }
    }

    @Nullable
    @Override
    public String validateInput() {
      for(ChangesBrowserSettingsEditor<?> editor: myEditors.values()) {
        String result = editor.validateInput();
        if (result != null) return result;
      }
      return null;
    }

    @Override
    public void updateEnabledControls() {
      for(ChangesBrowserSettingsEditor<?> editor: myEditors.values()) {
        editor.updateEnabledControls();
      }
    }

    @NotNull
    @Override
    public String getDimensionServiceKey() {
      @NonNls StringBuilder result = new StringBuilder();
      result.append("Composite");
      for(AbstractVcs vcs: myBaseVcss) {
        result.append(".").append(vcs.getDisplayName());
      }
      return result.toString();
    }
  }
}
