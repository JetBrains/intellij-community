// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package first

import second.<caret>

// EXIST: {"lookupString":"JavaClass","tailText":" (second)","icon":"RowIcon(icons=[Class, null])","attributes":"","allLookupStrings":"JavaClass","itemText":"JavaClass"}
// EXIST: {"lookupString":"JavaInterface","tailText":" (second)","icon":"RowIcon(icons=[Interface, null])","attributes":"","allLookupStrings":"JavaInterface","itemText":"JavaInterface"}
// NOTHING_ELSE
