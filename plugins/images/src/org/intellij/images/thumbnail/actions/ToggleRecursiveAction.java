/** $Id$ */
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
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.AbstractThumbnailViewToggleAction;

/**
 * Toggle recursive flag.
 *
 * @author <a href="aefimov@tengry.com">Alexey Efimov</a>
 */
public final class ToggleRecursiveAction extends AbstractThumbnailViewToggleAction {
    public void setSelected(ThumbnailView thumbnailView, AnActionEvent e, boolean state) {
        thumbnailView.setRecursive(state);
    }

    public boolean isSelected(ThumbnailView thumbnailView, AnActionEvent e) {
        return thumbnailView.isRecursive();
    }
}
