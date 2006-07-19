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
package org.intellij.images.editor;

/**
 * Location model presents bounds of image.
 * The zoom it calculated as y = exp(x/2).
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ImageZoomModel {
    int MACRO_ZOOM_LIMIT = 32;
    int MICRO_ZOOM_LIMIT = 8;

    double getZoomFactor();

    void setZoomFactor(double zoomFactor);

    void zoomOut();

    void zoomIn();

    boolean canZoomOut();

    boolean canZoomIn();
}
