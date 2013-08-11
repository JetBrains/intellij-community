/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;

/**
 * @author Aleksey Pivovarov
 *
 * All fields of the raw type are nullable by the nature of GSon parser;
 * but some of them are required, so we want them to be @NotNull in the actual data class,
 * otherwise there is an error in JSon data received from the server
 *
 * So we create Data class assuming that all required fields actually notnull and
 * catch exception if they are not.
 *
 * Probably, these interfaces shouldn't be used outside of GithubApiUtil.createDataFromRaw()
 */
interface DataConstructor {
  @NotNull
  <T> T create(@NotNull Class<T> resultClass) throws IllegalArgumentException, NullPointerException, ClassCastException;
}
