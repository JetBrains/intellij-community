/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.ui;

import com.siyeh.ig.BaseInspection;
import org.jetbrains.annotations.NonNls;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;

public class ToggleAction extends AbstractAction {

    private final BaseInspection owner;
    private final String propertyName;

    public ToggleAction(String labelText, BaseInspection owner,
                        @NonNls String propertyName) {
        this.owner = owner;
        this.propertyName = propertyName;
        putValue(Action.NAME, labelText);
        putValue(Action.SELECTED_KEY, getPropertyValue());
    }

    public void actionPerformed(ActionEvent event) {
        AbstractButton button = (AbstractButton)event.getSource();
        final boolean selected = button.isSelected();
        try {
            final Class<? extends BaseInspection> aClass =
                    owner.getClass();
            final Field field = aClass.getField(propertyName);
            field.setBoolean(owner, selected);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private Boolean getPropertyValue() {
        try {
            final Class<? extends BaseInspection> aClass = owner.getClass();
            final Field field = aClass.getField(propertyName);
            final Object object = field.get(owner);
            assert object instanceof Boolean;
            return (Boolean)object;
        } catch (Exception e) {
            return Boolean.FALSE;
        }
    }
}