/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

/**
 * for IDEA changelist name, IDEA changelist comment editing,
 * when, for example, under Perforce, comment corresponds to whole Perforce native changelist description,
 * while name is only a piece of description
 *
 * in that case, editing handler should be set for changelist in order to always edit name and comment consistently
 */
public interface ChangeListEditHandler {
  String changeCommentOnChangeName(final String name, final String comment);
  String changeNameOnChangeComment(final String name, final String comment);
  String correctCommentWhenInstalled(final String name, final String comment);
}
