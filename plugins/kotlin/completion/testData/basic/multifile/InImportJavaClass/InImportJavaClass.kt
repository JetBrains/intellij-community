// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package first

import second.<caret>

// EXIST: {"lookupString":"JavaClass","tailText":" (second)","icon":"fileTypes/java.svg","attributes":"","allLookupStrings":"JavaClass","itemText":"JavaClass"}
// EXIST: {"lookupString":"JavaInterface","tailText":" (second)","icon":"fileTypes/java.svg","attributes":"","allLookupStrings":"JavaInterface","itemText":"JavaInterface"}
// NOTHING_ELSE
