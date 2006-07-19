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
package org.intellij.images.thumbnail.actionSystem;

import org.jetbrains.annotations.NonNls;

/**
 * Editor actions.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ThumbnailViewActions {
    @NonNls String GROUP_POPUP = "Images.ThumbnailsPopupMenu";
    @NonNls String GROUP_TOOLBAR = "Images.ThumbnailsToolbar";
    @NonNls String ACTION_PLACE = "Images.Thumbnails";
}
