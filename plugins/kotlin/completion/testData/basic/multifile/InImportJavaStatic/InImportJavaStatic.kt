// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package first

import second.InImportJavaStatic.<caret>

// EXIST: {"lookupString":"CONST","typeText":"String!","icon":"fileTypes/java.svg","itemText":"CONST"}
// EXIST: {"lookupString":"statMeth","tailText":"()","typeText":"Unit","icon":"fileTypes/java.svg","allLookupStrings":"statMeth","itemText":"statMeth"}
// NOTHING_ELSE