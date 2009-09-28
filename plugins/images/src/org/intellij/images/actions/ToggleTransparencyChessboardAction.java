/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.intellij.images.ui.ImageComponentDecorator;

/**
 * Show/hide background action.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see org.intellij.images.ui.ImageComponentDecorator#setTransparencyChessboardVisible
 */
public final class ToggleTransparencyChessboardAction extends ToggleAction {
    public boolean isSelected(AnActionEvent e) {
        ImageComponentDecorator decorator = (ImageComponentDecorator) e.getDataContext().getData(ImageComponentDecorator.class.getName());
        return decorator != null && decorator.isEnabledForActionPlace(e.getPlace()) && decorator.isTransparencyChessboardVisible();
    }

    public void setSelected(AnActionEvent e, boolean state) {
        ImageComponentDecorator decorator = (ImageComponentDecorator) e.getDataContext().getData(ImageComponentDecorator.class.getName());
        if (decorator != null && decorator.isEnabledForActionPlace(e.getPlace())) {
            decorator.setTransparencyChessboardVisible(state);
        }
    }

    public void update(final AnActionEvent e) {
        super.update(e);
        ImageComponentDecorator decorator = (ImageComponentDecorator) e.getDataContext().getData(ImageComponentDecorator.class.getName());
        e.getPresentation().setEnabled(decorator != null && decorator.isEnabledForActionPlace(e.getPlace()));
    }
}
