/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.elements;

/**
 * Represents the state of an element.
 */
public enum ElementState {
  TO_BE_ADDED, // Does not exist on file, should be added.
  TO_BE_REMOVED, // Exists on file but should be deleted.
  EXISTING, // Exists on file and should stay there.
  HIDDEN, // Exists on file but invisible to the model.
  APPLIED, // These properties come from another file. These elements are not updated with calls to apply/create/delete.
  DEFAULT, // These properties do not exist on file at all, but represent their default value.
  MOVED, // These properties should be moved.
}
