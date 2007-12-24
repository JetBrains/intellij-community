package com.maddyhome.idea.copyright.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;
import com.intellij.util.containers.HashMap;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.ExternalOptionHelper;
import com.maddyhome.idea.copyright.options.Options;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.*;

public class CopyrightProfilesPanel extends MasterDetailsComponent {
    private static final Icon COPY_ICON = IconLoader.getIcon("/actions/copy.png");

    private Project myProject;
    private CopyrightManager myManager;

    public CopyrightProfilesPanel(Project project) {
        myProject = project;
        myManager = CopyrightManager.getInstance(project);
        initTree();
    }

    protected void processRemovedItems() {
        Map<String, CopyrightProfile> profiles = getAllProfiles();
        final List<CopyrightProfile> deleted = new ArrayList<CopyrightProfile>();
        for (CopyrightProfile profile : myManager.getCopyrights()) {
            if (!profiles.containsValue(profile)) {
                deleted.add(profile);
            }
        }
        for (CopyrightProfile profile : deleted) {
            myManager.removeCopyright(profile);
        }
    }

    protected boolean wasObjectStored(Object o) {
        return myManager.getCopyrights().contains((CopyrightProfile) o);
    }

    @Nls
    public String getDisplayName() {
        return "Copyright Profiles";
    }

    @Nullable
    public Icon getIcon() {
        return null;
    }

    @Nullable
    @NonNls
    public String getHelpTopic() {
        return null;
    }

    public void apply() throws ConfigurationException {
        final Set<String> profiles = new HashSet<String>();
        for (int i = 0; i < myRoot.getChildCount(); i++) {
            MyNode node = (MyNode) myRoot.getChildAt(i);
            final String profileName = ((CopyrightConfigurable) node.getConfigurable()).getEditableObject().getName();
            if (profiles.contains(profileName)) {
                selectNodeInTree(profileName);
                throw new ConfigurationException("Duplicate copyright profile name: \'" + profileName + "\'");
            }
            profiles.add(profileName);
        }
        super.apply();
    }

    public Map<String, CopyrightProfile> getAllProfiles() {
        final Map<String, CopyrightProfile> profiles = new HashMap<String, CopyrightProfile>();
        for (int i = 0; i < myRoot.getChildCount(); i++) {
            MyNode node = (MyNode) myRoot.getChildAt(i);
            final CopyrightProfile copyrightProfile = ((CopyrightConfigurable) node.getConfigurable()).getEditableObject();
            profiles.put(copyrightProfile.getName(), copyrightProfile);
        }
        return profiles;
    }

    @Nullable
    protected ArrayList<AnAction> createActions(boolean fromPopup) {
        ArrayList<AnAction> result = new ArrayList<AnAction>();
        result.add(new AnAction("Add", "Add", Icons.ADD_ICON) {
            {
                registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
            }
            public void actionPerformed(AnActionEvent event) {
                final String name = askForProfileName("Create new copyright profile");
                if (name == null) return;
                final CopyrightProfile copyrightProfile = new CopyrightProfile(name);
                addProfileNode(copyrightProfile);
            }


        });
        result.add(new MyDeleteAction(Conditions.alwaysTrue()));
        result.add(new AnAction("Copy", "Copy", COPY_ICON) {
            {
                registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_MASK)), myTree);
            }
            public void actionPerformed(AnActionEvent event) {
                final String profileName = askForProfileName("Copy copyright profile");
                if (profileName == null) return;
                final CopyrightProfile clone = new CopyrightProfile();
                clone.copyFrom((CopyrightProfile) getSelectedObject());
                clone.setName(profileName);
                addProfileNode(clone);
            }

            public void update(AnActionEvent event) {
                super.update(event);
                event.getPresentation().setEnabled(getSelectedObject() != null);
            }
        });
        result.add(new AnAction("Import", "Import", Icons.ADVICE_ICON) {
            public void actionPerformed(AnActionEvent event) {
                Options external = ExternalOptionHelper.getExternalOptions(myProject);
                if (external != null) {
                    final String profileName = askForProfileName("Import copyright profile");
                    if (profileName == null) return;
                    final CopyrightProfile copyrightProfile = new CopyrightProfile();
                    copyrightProfile.setName(profileName);
                    copyrightProfile.setOptions(external);
                    addProfileNode(copyrightProfile);
                    JOptionPane.showMessageDialog(null,
                            "The copyright settings have been successfully imported.",
                            "Import Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "The selected file did not contain any copyright settings.",
                            "Import Failure",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        return result;
    }

    private String askForProfileName(String title) {
        return Messages.showInputDialog("New copyright profile name:", title, Messages.getQuestionIcon(), "", new InputValidator() {
            public boolean checkInput(String s) {
                return !getAllProfiles().containsKey(s) && s.length() > 0;
            }

            public boolean canClose(String s) {
                return checkInput(s);
            }
        });
    }

    private void addProfileNode(CopyrightProfile copyrightProfile) {
        final CopyrightConfigurable copyrightConfigurable = new CopyrightConfigurable(myProject, copyrightProfile, TREE_UPDATER);
        copyrightConfigurable.setModified(true);
        final MyNode node = new MyNode(copyrightConfigurable);
        addNode(node, myRoot);
        selectNodeInTree(node);
    }

    private void reloadTree() {
        myRoot.removeAllChildren();
        Collection<CopyrightProfile> collection = myManager.getCopyrights();
        for (CopyrightProfile profile : collection) {
            CopyrightProfile clone = new CopyrightProfile();
            clone.copyFrom(profile);
            addNode(new MyNode(new CopyrightConfigurable(myProject, clone, TREE_UPDATER)), myRoot);
        }
    }

    public void reset() {
        reloadTree();
        super.reset();
    }
}
