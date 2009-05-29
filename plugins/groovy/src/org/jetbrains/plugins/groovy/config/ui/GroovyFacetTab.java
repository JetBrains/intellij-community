/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.facet.impl.ui.FacetContextChangeListener;
import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.facet.ui.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.grails.config.GrailsLibraryManager;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.GroovyFacetConfiguration;
import org.jetbrains.plugins.groovy.config.GroovyLibraryManager;
import org.jetbrains.plugins.groovy.config.LibraryManager;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author ilyas
 */
public class GroovyFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.config.ui.GroovyFacetTab");

  private final Module myModule;
  private JPanel myPanel;
  private JRadioButton myCompile;
  private JRadioButton myCopyToOutput;
  private JButton myAddButton;
  private JButton myRemoveButton;
  private JList myLibraryList;
  private JButton myModuleDeps;
  private JCheckBox myIsGrails;
  private final ProjectSettingsContext myEditorContext;

  private final GroovyFacetConfiguration myConfiguration;
  private final FacetValidatorsManager myValidatorsManager;
  private final DefaultListModel myListModel;
  private LibrariesContainer myLibrariesContainer;
  private final LibraryManager[] myManagers = LibraryManager.EP_NAME.getExtensions();

  public GroovyFacetTab(final FacetEditorContext editorContext, GroovyFacetConfiguration configuration, FacetValidatorsManager validatorsManager) {
    myConfiguration = configuration;
    myValidatorsManager = validatorsManager;
    myEditorContext = (ProjectSettingsContext)editorContext;
    myModule = myEditorContext.getModule();
    myLibrariesContainer = ((FacetEditorContextBase)editorContext).getContainer();

    myListModel = new DefaultListModel();

    setUpLibraryList();

    ((FacetEditorContextBase)editorContext).addFacetContextChangeListener(new FacetContextChangeListener() {
      public void moduleRootsChanged(ModifiableRootModel rootModel) {
        updateLibraryList();
        final boolean hasGrails = hasGrailsLibrary();
        if (!myIsGrails.isEnabled() && hasGrails) {
          myIsGrails.setEnabled(true);
          myIsGrails.setSelected(true);
        } else if (!hasGrails) {
          myIsGrails.setEnabled(false);
          myIsGrails.setSelected(false);
        }
      }

      public void facetModelChanged(@NotNull Module module) {
      }
    });
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        performAddAction(myAddButton, false);
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

    validatorsManager.registerValidator(new FacetEditorValidator() {
      @Override
      public ValidationResult check() {
        final Set<ManagedLibrary> libraries = getUsedLibraries();
        for (final ManagedLibrary library : libraries) {
          if (isGroovyOrGrails(library.manager)) {
            return ValidationResult.OK;
          }
        }

        return new ValidationResult("Groovy/Grails is not configured yet", new FacetConfigurationQuickFix() {
          @Override
          public void run(JComponent place) {
            performAddAction(place, true);
          }
        });
      }
    });
  }

  private boolean hasGrailsLibrary() {
    for (ManagedLibrary library : getUsedLibraries()) {
      if (library.manager instanceof GrailsLibraryManager) {
        return true;
      }
    }
    return false;
  }

  private void performAddAction(final JComponent component, boolean mainOnly) {
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
      if (mainOnly && !isGroovyOrGrails(manager)) {
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

  private static boolean isGroovyOrGrails(final LibraryManager manager) {
    return manager instanceof GrailsLibraryManager || manager instanceof GroovyLibraryManager;
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
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
        final Library library = libraryOrderEntry.getLibrary();
        if (library != null && toDelete.contains(library)) {
          rootModel.removeOrderEntry(libraryOrderEntry);
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
        append(ml.library.getName());
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

    AnAction navigateAction = new AnAction("&Show in module dependencies") {

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(hasSelection());
      }

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
    };
    navigateAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(), myLibraryList);

    final AnAction removeAction = new AnAction("R&emove...") {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(hasSelection());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        deleteSelectedLibraries();
      }
    };
    removeAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE).getShortcutSet(), myLibraryList);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(navigateAction);
    actionGroup.add(removeAction);
    PopupHandler.installPopupHandler(myLibraryList, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
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

  private void updateLibraryList() {
    myListModel.clear();
    Set<LibraryManager> usedManagers = new HashSet<LibraryManager>();
    for (final ManagedLibrary library : getUsedLibraries()) {
      myListModel.addElement(library);
      usedManagers.add(library.manager);
    }

    myAddButton.setEnabled(usedManagers.size() < myManagers.length);
    myValidatorsManager.validate();
  }

  @Nls
  public String getDisplayName() {
    return GroovyBundle.message("groovy.sdk.configuration");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    if (myCompile.isSelected() != myConfiguration.isCompileGroovyFiles()) {
      return true;
    }
    if (isGrailsApplication() != myConfiguration.getGrailsApplication()) {
      return true;
    }

    return false;
  }

  @Override
  public String getHelpTopic() {
    return super.getHelpTopic();
  }

  public void apply() throws ConfigurationException {
    myConfiguration.setCompileGroovyFiles(myCompile.isSelected());
    myConfiguration.setGrailsApplication(isGrailsApplication());
  }

  @Nullable
  private Boolean isGrailsApplication() {
    return myIsGrails.isEnabled() ? myIsGrails.isSelected() : null;
  }

  public void reset() {
    (myConfiguration.isCompileGroovyFiles() ? myCompile : myCopyToOutput).setSelected(true);
    final Boolean isGrails = myConfiguration.getGrailsApplication();
    myIsGrails.setEnabled(isGrails != null);
    myIsGrails.setSelected(isGrails != null && isGrails.booleanValue());
    updateLibraryList();
  }

  public void disposeUIResources() {
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
        final String name = ml.library.getName();

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
            final LibraryManager manager = managedLibrary.manager;
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
                  Messages.showDialog(myPanel, message, "Library already exists", new String[]{replace, "&Add", "&Cancel"}, 0, null);
                if (result == 2 || result < 0) {
                  return; //cancel
                }

                if (result == 0) {
                  doRemoveLibraries(Collections.singleton(existing.library));
                }
              }
            }

            myEditorContext.getModifiableRootModel().addLibraryEntry(managedLibrary.library);
          }
          else if (selectedValue instanceof LibraryManager) {
            final Library library = ((LibraryManager)selectedValue).createLibrary(myEditorContext);
            if (library == null) {
              return;
            }
            myEditorContext.getModifiableRootModel().addLibraryEntry(library);
          }

          updateLibraryList();
        }
      }, ModalityState.stateForComponent(myPanel));

      return FINAL_CHOICE;
    }
  }

}
