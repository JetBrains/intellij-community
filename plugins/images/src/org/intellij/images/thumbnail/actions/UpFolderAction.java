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
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.AbstractThumbnailViewAction;

/**
 * Level up to browse images.
 *
 * @author <a href="aefimov@tengry.com">Alexey Efimov</a>
 */
public final class UpFolderAction extends AbstractThumbnailViewAction {
    public void actionPerformed(ThumbnailView thumbnailView, AnActionEvent e) {
        VirtualFile root = thumbnailView.getRoot();
        if (root != null) {
            VirtualFile parent = root.getParent();
            if (parent != null) {
                thumbnailView.setRoot(parent);
            }
        }
    }

    public void update(ThumbnailView thumbnailView, AnActionEvent e) {
        VirtualFile root = thumbnailView.getRoot();
        e.getPresentation().setEnabled(root != null ? root.getParent() != null && !thumbnailView.isRecursive() : false);
    }
}
