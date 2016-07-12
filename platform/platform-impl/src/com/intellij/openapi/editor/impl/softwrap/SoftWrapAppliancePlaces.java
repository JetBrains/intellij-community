/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap;

/**
 * Enumerates distinct places where soft wraps may be used.
 * <p/>
 * The general idea is that we may want to allow configure distinct soft wrap settings for distinct use-cases. E.g. we may want
 * to use soft wraps at console editor but avoid using them at the main editor etc.
 *
 * @author Denis Zhdanov
 * @since 9/30/10 7:32 PM
 */
public enum SoftWrapAppliancePlaces {
  MAIN_EDITOR, CONSOLE, PREVIEW
}
