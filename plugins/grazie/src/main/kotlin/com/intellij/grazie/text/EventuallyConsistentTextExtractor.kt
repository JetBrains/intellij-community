package com.intellij.grazie.text

import org.jetbrains.annotations.ApiStatus

/**
 * Used in Rider, where provided TextContent can be inconsistent with actual PsiElement,
 * since TextContent for R# languages built on highlightings from backend.
 */
@ApiStatus.Internal
interface EventuallyConsistentTextExtractor