// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

class GitLabGraphQLMutationEmptyResultException
  : GitLabGraphQLMutationException("Mutation returned empty result")