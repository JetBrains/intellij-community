// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptAddContextDecisionsTest {
    @Test
    fun fingerprintIgnoresContributorPhaseAndTruncation() {
        val first = contextItem(
            phase = AgentPromptContextContributorPhase.INVOCATION,
            truncation = AgentPromptContextTruncation(
                originalChars = 20,
                includedChars = 10,
                reason = AgentPromptContextTruncationReason.SOURCE_LIMIT
            ),
        )
        val second = contextItem(
            phase = AgentPromptContextContributorPhase.FALLBACK,
            truncation = AgentPromptContextTruncation(
                originalChars = 100,
                includedChars = 25,
                reason = AgentPromptContextTruncationReason.SOFT_CAP_PARTIAL
            ),
        )

        assertThat(addContextItemFingerprint(first)).isEqualTo(addContextItemFingerprint(second))
    }

    @Test
    fun fingerprintIsStableAcrossPayloadObjectKeyOrdering() {
        val payloadA = AgentPromptPayloadValue.Obj(
            linkedMapOf(
                "b" to AgentPromptPayload.str("2"),
                "a" to AgentPromptPayload.str("1"),
            )
        )
        val payloadB = AgentPromptPayloadValue.Obj(
            linkedMapOf(
                "a" to AgentPromptPayload.str("1"),
                "b" to AgentPromptPayload.str("2"),
            )
        )

        assertThat(addContextItemFingerprint(contextItem(payload = payloadA)))
            .isEqualTo(addContextItemFingerprint(contextItem(payload = payloadB)))
    }

    @Test
    fun appendUniqueItemsDropsExistingAndSameBatchDuplicates() {
        val existing = contextItem(body = "return 42")
        val duplicateExisting = contextItem(body = "  return   42  ")
        val unique = contextItem(body = "return 43")
        val duplicateUnique = contextItem(body = "return 43")

        val appended = appendUniqueAddContextItems(
            currentItems = listOf(existing),
            candidateItems = listOf(duplicateExisting, unique, duplicateUnique),
        )

        assertThat(appended).containsExactly(unique)
    }

    private fun contextItem(
        body: String = "return 42",
        payload: AgentPromptPayloadValue = AgentPromptPayloadValue.Obj.EMPTY,
        phase: AgentPromptContextContributorPhase? = null,
        truncation: AgentPromptContextTruncation = AgentPromptContextTruncation.none(body.length),
    ): AgentPromptContextItem {
        return AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.SNIPPET,
            title = "Snippet",
            body = body,
            payload = payload,
            itemId = "snippet:1",
            parentItemId = "file:1",
            source = "test",
            phase = phase,
            truncation = truncation,
        )
    }
}
