/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.breakout;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class BreakoutMode implements ApplicationComponent {
    private static final Key<Boolean> ENABLED = Key.create("EditorBreakoutMode.enabled");
    private final EventListener eventListener = new EventListener();
    private final ToggleAction action = new ToggleBreakoutModeAction();
    private final AnimationManager animationManager = new AnimationManager();

    public static BreakoutMode getInstance() {
        return ApplicationManager.getApplication().getComponent(BreakoutMode.class);
    }

    public BreakoutMode() {
    }

    public void initComponent() {
        EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
        editorEventMulticaster.addEditorMouseListener(eventListener);
        editorEventMulticaster.addEditorMouseMotionListener(eventListener);
        ActionManager actionManager = ActionManager.getInstance();
        actionManager.addAnActionListener(eventListener);
        AnAction group = actionManager.getAction(IdeActions.GROUP_EDITOR_POPUP);
        if (group instanceof DefaultActionGroup) {
            ((DefaultActionGroup) group).add(action, new Constraints(Anchor.AFTER, "EditorToggleColumnMode"));
        }
    }

    public void disposeComponent() {
        EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
        editorEventMulticaster.removeEditorMouseListener(eventListener);
        editorEventMulticaster.removeEditorMouseMotionListener(eventListener);
        ActionManager actionManager = ActionManager.getInstance();
        actionManager.removeAnActionListener(eventListener);
        AnAction group = actionManager.getAction(IdeActions.GROUP_EDITOR_POPUP);
        if (group instanceof DefaultActionGroup) {
            ((DefaultActionGroup) group).remove(action);
        }
    }

    public boolean isEnabled(@Nullable Editor editor) {
        if (!(editor instanceof EditorImpl)) {
            return false;
        }
        Boolean enabled = editor.getUserData(ENABLED);
        return enabled != null && enabled;
    }

    public void setEnabled(@Nullable Editor editor, boolean enabled) {
        if (!(editor instanceof EditorImpl)) {
            return;
        }
        editor.putUserData(ENABLED, enabled);
        if (!enabled) {
            animationManager.stopAnimation((EditorImpl) editor);
        }
    }

    public boolean isAllowed() {
        GregorianCalendar currentDate = new GregorianCalendar();
        return currentDate.get(Calendar.MONTH) == Calendar.APRIL && currentDate.get(Calendar.DAY_OF_MONTH) == 1;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    @NotNull
    public String getComponentName() {
        return "AngryDeveloperComponent";
    }
}
