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
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.actionSystem.ImageEditorActionUtil;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;

/**
 * Show/hide background action.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#setTransparencyChessboardVisible
 * @see ThumbnailView#setTransparencyChessboardVisible
 */
public final class ToggleTransparencyChessboardAction extends ToggleAction {
    public boolean isSelected(AnActionEvent e) {
        ImageEditor editor = ImageEditorActionUtil.getEditor(e);
        if (editor != null) {
            return editor.isValid() && editor.isTransparencyChessboardVisible();
        }
        ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
        return view != null && view.isTransparencyChessboardVisible();
    }

    public void setSelected(AnActionEvent e, boolean state) {
        ImageEditor editor = ImageEditorActionUtil.getEditor(e);
        if (editor != null) {
            if (editor.isValid()) {
                editor.setTransparencyChessboardVisible(state);
            }
        } else {
            ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
            if (view != null) {
                view.setTransparencyChessboardVisible(state);
            }
        }
    }

    public void update(final AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(false);
        ImageEditor editor = ImageEditorActionUtil.getEditor(e);
        if (editor != null) {
            if (editor.isValid()) {
                e.getPresentation().setEnabled(true);
            }
        } else {
            ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
            if (view != null) {
                e.getPresentation().setEnabled(true);
            }
        }
    }
}
