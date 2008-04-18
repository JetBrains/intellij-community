/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.refactoring;

import org.intellij.lang.xpath.xslt.XsltConfig;
import org.intellij.lang.xpath.xslt.refactoring.extractTemplate.XsltExtractTemplateAction;
import org.intellij.lang.xpath.xslt.refactoring.introduceParameter.XsltIntroduceParameterAction;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class XsltRefactoringSupport implements ApplicationComponent {
    private static final Logger LOG = Logger.getInstance("org.intellij.lang.xpath.xslt.refactoring.XsltRefactoringSupport");

    private static final String REFACTORING_MENU_ID = "RefactoringMenu";
//    private static final String INTRODUCE_VARIABLE = "IntroduceVariable";
    private static final String INTRODUCE_PARAMETER = "IntroduceParameter";
    private static final String EXTRACT_METHOD = "ExtractMethod";
    private static final String INLINE = "Inline";

    private final XsltConfig myConfig;
    private final Map<String, HookedAction> myActions = new HashMap<String, HookedAction>();
    private boolean myInstalled;

    public XsltRefactoringSupport(XsltConfig config) {
        myConfig = config;
    }

    @NotNull
    public String getComponentName() {
        return "XsltRefactoringSupport.Installer";
    }

    public void initComponent() {
        if (myConfig.isEnabled()) {
            install(false);
        }
    }

    public void disposeComponent() {
        if (myInstalled) {
            uninstall(false);
        }
    }

    public void setEnabled(boolean b) {
        if (!myInstalled && b) {
            install(true);
        } else if (myInstalled && !b) {
            uninstall(true);
        }
    }
    
    private void install(boolean full) {
        LOG.assertTrue(!myInstalled);

//        install(INTRODUCE_VARIABLE, new XsltIntroduceVariableAction(), full);
        install(INTRODUCE_PARAMETER, new XsltIntroduceParameterAction(), full);
        install(INLINE, new XsltInlineAction(), full);
        install(EXTRACT_METHOD, new XsltExtractTemplateAction(), full);

        myInstalled = true;
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    private void install(String actionName, XsltRefactoringActionBase newAction, boolean full) {
        final ActionManagerEx amgr = ActionManagerEx.getInstanceEx();

        final AnAction action = amgr.getAction(actionName);
        newAction.setHookedAction(action);

        if (full) {
            fullInstall(action, newAction, amgr);
        }

        amgr.unregisterAction(actionName);
        amgr.registerAction(actionName, newAction);
        myActions.put(actionName, newAction);
    }

    private void fullInstall(AnAction action, AnAction newAction, ActionManagerEx amgr) {
        final AnAction menu = amgr.getAction(REFACTORING_MENU_ID);

        if (menu instanceof DefaultActionGroup) {
            final DefaultActionGroup actionGroup = ((DefaultActionGroup)menu);
            final AnAction[] children = actionGroup.getChildren(null);
            for (int i = 0; i < children.length; i++) {
                final AnAction child = children[i];

                if (child == action) {
                    if (!(child instanceof HookedAction)) {
                        replaceAction(child, i, newAction, actionGroup, amgr);
                    }
                    break;
                }
            }
        }
    }

    private void uninstall(boolean full) {
        LOG.assertTrue(myInstalled);

        final ActionManagerEx amgr = ActionManagerEx.getInstanceEx();

        final Set<String> actionNames = myActions.keySet();
        for (String name : actionNames) {
            final AnAction action = amgr.getAction(name);

            if (action == myActions.get(name)) {
                final AnAction origAction = ((HookedAction)action).getHookedAction();
                if (full) {
                    fullUninstall(action, origAction, amgr);
                }

                amgr.unregisterAction(name);
                amgr.registerAction(name, origAction);
            } else {
                LOG.info("Cannot uninstall action '" + name + "'. It has been hooked by another action: " + action.getClass().getName());
            }
        }

        myActions.clear();
        myInstalled = false;
    }

    private void fullUninstall(AnAction action, AnAction origAction, ActionManagerEx amgr) {
        final AnAction menu = amgr.getAction(REFACTORING_MENU_ID);

        if (menu instanceof DefaultActionGroup) {
            final DefaultActionGroup actionGroup = ((DefaultActionGroup)menu);
            final AnAction[] children = actionGroup.getChildren(null);
            for (int i = 0; i < children.length; i++) {
                final AnAction child = children[i];

                if (child == action) {
                    replaceAction(child, i, origAction, actionGroup, amgr);
                    break;
                }
            }
        }
    }

    private static void replaceAction(AnAction child, int i, AnAction newAction, DefaultActionGroup actionGroup, ActionManager amgr) {
        AnAction[] children = actionGroup.getChildren(null);
        actionGroup.remove(child);
        final Constraints constraint;
        if (i == 0) {
            constraint = new Constraints(Anchor.FIRST, null);
        } else {
            final AnAction prevChild = children[i - 1];
            if (prevChild instanceof Separator) {
                if (i < children.length - 1) {
                    constraint = new Constraints(Anchor.BEFORE, amgr.getId(children[i + 1]));
                } else {
                    constraint = new Constraints(Anchor.LAST, null);
                }
            } else {
                constraint = new Constraints(Anchor.AFTER, amgr.getId(prevChild));
            }
        }
        actionGroup.add(newAction, constraint);
    }

    public static XsltRefactoringSupport getInstance() {
        return ApplicationManager.getApplication().getComponent(XsltRefactoringSupport.class);
    }
}
