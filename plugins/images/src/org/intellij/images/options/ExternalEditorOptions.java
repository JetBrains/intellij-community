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
package org.intellij.images.options;

import org.jetbrains.annotations.NonNls;

/**
 * External editor options.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ExternalEditorOptions extends Cloneable {
    @NonNls
    String ATTR_PREFIX = "ExternalEditor.";
    @NonNls
    String ATTR_EXECUTABLE_PATH = ATTR_PREFIX + "executablePath";

    String getExecutablePath();

    void inject(ExternalEditorOptions options);

    boolean setOption(String name, Object value);
}
