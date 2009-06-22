/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.facet.impl.ui.FacetContextChangeListener;
import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.facet.ui.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.LibraryManager;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author peter
 */
public class ManagedLibrariesEditor {
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JList myLibraryList;
  private JButton myModuleDeps;
  private JPanel myComponent;

  private final ProjectSettingsContext myEditorContext;
  private final FacetValidatorsManager myValidatorsManager;
  private final DefaultListModel myListModel;
  private final LibrariesContainer myLibrariesContainer;
  private final LibraryManager[] myManagers;
  private final Module myModule;

  public ManagedLibrariesEditor(final FacetEditorContext editorContext, FacetValidatorsManager validatorsManager, final ExtensionPointName<? extends LibraryManager> epName) {
    myEditorContext = (ProjectSettingsContext)editorContext;
    myValidatorsManager = validatorsManager;
    myModule = myEditorContext.getModule();
    myLibrariesContainer = getFacetEditorContext().getContainer();
    myManagers = epName.getExtensions();

    myListModel = new DefaultListModel();

    setUpLibraryList();
    installPopup();
    installButtonListeners(editorContext);

    getFacetEditorContext().addFacetContextChangeListener(new FacetContextChangeListener() {
      public void moduleRootsChanged(ModifiableRootModel rootModel) {
        updateLibraryList();
      }

      public void facetModelChanged(@NotNull Module module) {
      }
    });
  }

  private void installButtonListeners(final FacetEditorContext editorContext) {
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        //noinspection unchecked
        performAddAction(myAddButton, Condition.TRUE);
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        deleteSelectedLibraries();
      }
    });
    myModuleDeps.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ModuleStructureConfigurable.getInstance(editorContext.getProject()).selectOrderEntry(myModule, null);
      }
    });
  }

  public void shouldHaveLibrary(final Condition<LibraryManager> filter, final String errorMessage) {
    myValidatorsManager.registerValidator(new FacetEditorValidator() {
      @Override
      public ValidationResult check() {
        for (ManagedLibrary library : getUsedLibraries()) {
          if (filter.value(library.manager)) {
            return ValidationResult.OK;
          }
        }

        return new ValidationResult(errorMessage, new FacetConfigurationQuickFix() {
          @Override
          public void run(JComponent place) {
            performAddAction(place, filter);
          }
        });
      }
    });

  }

  public FacetEditorContextBase getFacetEditorContext() {
    return (FacetEditorContextBase)myEditorContext;
  }

  public JPanel getComponent() {
    return myComponent;
  }

  private void performAddAction(final JComponent component, Condition<LibraryManager> filter) {
    final Set<ManagedLibrary> managed = getUsedLibraries();
    final Set<Library> usedLibraries = ContainerUtil.map2Set(managed, new Function<ManagedLibrary, Library>() {
      public Library fun(ManagedLibrary managedLibrary) {
        return managedLibrary.library;
      }
    });

    final MultiMap<LibraryManager, ManagedLibrary> libs = new MultiMap<LibraryManager, ManagedLibrary>();

    final List<Object> toAdd = CollectionFactory.arrayList();
    final Map<Object, ListSeparator> separators = CollectionFactory.newTroveMap();

    for (Library library : myLibrariesContainer.getAllLibraries()) {
      if (!usedLibraries.contains(library)) {
        final LibraryManager manager = findManagerFor(library);
        if (manager != null) {
          libs.putValue(manager, new ManagedLibrary(library, manager));
        }
      }
    }

    for (LibraryManager manager : myManagers) {
      if (!filter.value(manager)) {
        continue;
      }

      boolean separatorSet = false;
      for (ManagedLibrary library : libs.get(manager)) {
        if (!separatorSet) {
          separators.put(library, new ListSeparator(manager.getLibraryCategoryName()));
          separatorSet = true;
        }
        toAdd.add(library);
      }
      if (!separatorSet) {
        separators.put(manager, new ListSeparator(manager.getLibraryCategoryName()));
      }
      toAdd.add(manager);
    }

    JBPopupFactory.getInstance().createListPopup(new ManagedLibrariesPopupStep(toAdd, separators)).showUnderneathOf(component);
  }

  private void deleteSelectedLibraries() {
    Set<Library> toDelete = new HashSet<Library>();
    for (final Object value : myLibraryList.getSelectedValues()) {
      toDelete.add(((ManagedLibrary) value).library);
    }
    doRemoveLibraries(toDelete);
    updateLibraryList();
  }

  private void doRemoveLibraries(Set<Library> toDelete) {
    final ModifiableRootModel rootModel = myEditorContext.getModifiableRootModel();
    for (OrderEntry entry : rootModel.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library != null && toDelete.remove(library)) {
          rootModel.removeOrderEntry(entry);
        }
      }
    }
  }

  private void setUpLibraryList() {
    myLibraryList.setModel(myListModel);

    myLibraryList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        ManagedLibrary ml = (ManagedLibrary)value;
        setIcon(ml.manager.getIcon());
        append(ml.getLibraryName());
        final String version = ml.version;
        if (StringUtil.isNotEmpty(version)) {
          append(" (version " + version + ")", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
      }
    });

    myLibraryList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myRemoveButton.setEnabled(hasSelection());
      }
    });
  }

  private void installPopup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(addShortcut(IdeActions.ACTION_EDIT_SOURCE, new ManagedLibraryAction("&Edit library...", false) {

      @Override
      public void actionPerformed(AnActionEvent e) {
        final Object value = myLibraryList.getSelectedValue();
        if (value instanceof ManagedLibrary) {
          final Library library = ((ManagedLibrary)value).library;
          final LibraryTable table = library.getTable();
          if (table == null) {
            return;
          }
          final LibraryTableModifiableModelProvider provider =
            ProjectStructureConfigurable.getInstance(myModule.getProject()).getContext().createModifiableModelProvider(table.getTableLevel(), false);
          LibraryTableEditor.editLibrary(provider, library).openDialog(myComponent, Collections.singletonList(library), true);
        }

      }
    }));
    actionGroup.add(addShortcut(IdeActions.ACTION_GOTO_DECLARATION, new ManagedLibraryAction("&Show in module dependencies", false) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final Object value = myLibraryList.getSelectedValue();
        if (value instanceof ManagedLibrary) {
          for (OrderEntry entry : myEditorContext.getModifiableRootModel().getOrderEntries()) {
            if (entry instanceof LibraryOrderEntry) {
              final LibraryOrderEntry orderEntry = (LibraryOrderEntry)entry;
              if (((ManagedLibrary)value).library.equals(orderEntry.getLibrary())) {
                ModuleStructureConfigurable.getInstance(myEditorContext.getProject()).selectOrderEntry(myModule, orderEntry);
                return;
              }
            }
          }
        }

      }
    }));
    actionGroup.add(addShortcut(IdeActions.ACTION_DELETE, new ManagedLibraryAction("R&emove...", true) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        deleteSelectedLibraries();
      }
    }));
    PopupHandler.installPopupHandler(myLibraryList, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  private ManagedLibraryAction addShortcut(final String fromAction, ManagedLibraryAction action) {
    action.registerCustomShortcutSet(ActionManager.getInstance().getAction(fromAction).getShortcutSet(), myLibraryList);
    return action;
  }

  private boolean hasSelection() {
    return myLibraryList.getSelectedIndices().length > 0;
  }

  private Set<ManagedLibrary> getUsedLibraries() {
    final LinkedHashSet<ManagedLibrary> libraries = new LinkedHashSet<ManagedLibrary>();
    for (OrderEntry entry : myEditorContext.getModifiableRootModel().getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library != null) {
          final LibraryManager manager = findManagerFor(library);
          if (manager != null) {
            libraries.add(new ManagedLibrary(library, manager));
          }
        }
      }
    }
    return libraries;
  }

  @Nullable
  private LibraryManager findManagerFor(@NotNull Library library) {
    for (final LibraryManager manager : myManagers) {
      if (manager.managesLibrary(library, myLibrariesContainer)) {
        return manager;
      }
    }
    return null;
  }

  @Nullable
  public Library findUsedLibrary(Class<? extends LibraryManager> managedBy) {
    for (final ManagedLibrary library : getUsedLibraries()) {
      if (managedBy.isInstance(library.manager)) {
        return library.library;
      }
    }
    return null;
  }

  public void updateLibraryList() {
    myListModel.clear();
    Set<LibraryManager> usedManagers = new HashSet<LibraryManager>();
    for (final ManagedLibrary library : getUsedLibraries()) {
      myListModel.addElement(library);
      usedManagers.add(library.manager);
    }

    myAddButton.setEnabled(usedManagers.size() < myManagers.length);
    myValidatorsManager.validate();
  }

  private class ManagedLibrary {
    final Library library;
    final LibraryManager manager;
    @Nullable final String version;

    private ManagedLibrary(@NotNull Library library, @NotNull LibraryManager manager) {
      this.library = library;
      this.manager = manager;
      version = manager.getLibraryVersion(library, myLibrariesContainer);
    }

    public String getLibraryName() {
      final String s = library.getName();
      return s == null ? "<noname>" : s;
    }
  }


  private class ManagedLibrariesPopupStep extends BaseListPopupStep<Object> {
    private final Map<Object, ListSeparator> mySeparators;

    public ManagedLibrariesPopupStep(List<Object> toAdd, Map<Object, ListSeparator> separators) {
      super(null, toAdd);
      mySeparators = separators;
    }

    @NotNull
    @Override
    public String getTextFor(Object value) {
      if (value instanceof ManagedLibrary) {
        final ManagedLibrary ml = (ManagedLibrary)value;
        final String name = ml.getLibraryName();

        //todo make it gray
        final String version = ml.version;
        if (StringUtil.isNotEmpty(version)) {
          return name + " (version " + version + ")";
        }
        return name;
      }
      return ((LibraryManager) value).getAddActionText();
    }

    @Override
    public Icon getIconFor(Object value) {
      if (value instanceof ManagedLibrary) {
        return Icons.LIBRARY_ICON;
      }
      return ((LibraryManager) value).getIcon();
    }

    @Override
    public ListSeparator getSeparatorAbove(Object value) {
      return mySeparators.get(value);
    }

    @Override
    public PopupStep onChosen(final Object selectedValue, boolean finalChoice) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (selectedValue instanceof ManagedLibrary) {
            final ManagedLibrary managedLibrary = (ManagedLibrary)selectedValue;
            addLibraryCheckingExistings(managedLibrary.manager, managedLibrary.library);
          }
          else if (selectedValue instanceof LibraryManager) {
            final LibraryManager manager = (LibraryManager)selectedValue;
            final Library library = manager.createLibrary(myEditorContext);
            if (library != null) {
              addLibraryCheckingExistings(manager, library);
            }
          }

          updateLibraryList();
        }
      }, ModalityState.stateForComponent(myComponent));

      return FINAL_CHOICE;
    }

    private void addLibraryCheckingExistings(LibraryManager manager, Library library) {
      for (ManagedLibrary existing : getUsedLibraries()) {
        if (existing.manager == manager) {
          @SuppressWarnings({"NonConstantStringShouldBeStringBuffer"})
          String message = "There is already a " + manager.getLibraryCategoryName() + " library";
          final String version = manager.getLibraryVersion(existing.library, myLibrariesContainer);
          if (StringUtil.isNotEmpty(version)) {
            message += " of version " + version;
          }
          message += ".\n Do you want to replace the existing one?";
          final String replace = "&Replace";
          final int result =
            Messages.showDialog(myComponent, message, "Library already exists", new String[]{replace, "&Add", "&Cancel"}, 0, null);
          if (result == 2 || result < 0) {
            return; //cancel or escape
          }

          if (result == 0) {
            doRemoveLibraries(Collections.singleton(existing.library));
          }
        }
      }

      myEditorContext.getModifiableRootModel().addLibraryEntry(library);
    }
  }

  private abstract class ManagedLibraryAction extends AnAction implements DumbAware {
    private final boolean myOnMultiSelection;

    protected ManagedLibraryAction(String text, boolean onMultiSelection) {
      super(text);
      myOnMultiSelection = onMultiSelection;
    }

    @Override
    public void update(AnActionEvent e) {
      final int selCount = myLibraryList.getSelectedIndices().length;
      e.getPresentation().setEnabled(myOnMultiSelection ? selCount > 0 : selCount == 1);
    }

  }

}
