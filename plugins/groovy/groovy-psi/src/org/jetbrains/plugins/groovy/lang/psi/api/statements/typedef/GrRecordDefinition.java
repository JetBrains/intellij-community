// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterListOwner;

public interface GrRecordDefinition extends GrTypeDefinition, GrTypeParameterListOwner, GrParameterListOwner {
}
