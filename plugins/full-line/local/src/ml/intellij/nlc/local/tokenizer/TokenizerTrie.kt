package ml.intellij.nlc.local.tokenizer

import kotlin.math.max
import kotlin.math.min

internal class TokenizerTrie(val tokenizer: Tokenizer) {
    private val sortedVocabEntries = tokenizer.vocab.toList().sortedBy { it.first }

    private val root = TrieNode()


    init {
        sortedVocabEntries.mapIndexed { index, pair ->
            root.addWord(pair.first, index)
        }
    }

    fun getValuesWithCompletionAwarePrefix(prefix: String): IntArray {
        val answer = getInternalValuesWithPrefix(prefix, strict = false)
        val answerStrings = answer.map { sortedVocabEntries[it].first }
        val completableAnswerStrings = answerStrings.filter {
            val subPrefixLen = it.length
            // TODO: filter by length
            if (prefix.length > subPrefixLen) {
                val toComplete = prefix.substring(subPrefixLen)
                return@filter getInternalValuesWithPrefix(toComplete, strict = true).isNotEmpty()
            } else {
                return@filter true
            }
        }
        return completableAnswerStrings.map { tokenizer.vocab.getValue(it) }.toIntArray()
    }

    fun getValuesWithPrefix(prefix: String, strict: Boolean): IntArray {
        return getInternalValuesWithPrefix(prefix, strict).map { sortedVocabEntries[it].second }.toIntArray()
    }

    private fun getInternalValuesWithPrefix(prefix: String, strict: Boolean): List<Int> {
        val (foundTrie, path) = findTrie(prefix)
        var answer = foundTrie?.subtrieValues ?: emptyList()
        if (!strict) answer = answer + path.mapNotNull { it.value }
        return answer
    }

    private fun findTrie(word: String): Pair<TrieNode?, List<TrieNode>> {
        assert(word.isNotEmpty()) { "Word must not be empty" }
        var curNode = this.root
        var newNode: TrieNode? = null
        val path = mutableListOf<TrieNode>()
        for (char in word) {
            path.add(curNode)
            newNode = curNode.moveByChar(char)
            newNode?.let { curNode = newNode } ?: break
        }
        return Pair(newNode, path)
    }

    internal class TrieNode {
        private val moves: MutableMap<Char, TrieNode> = mutableMapOf()
        var value: Int? = null
        private var start: Int? = null
        private var end: Int? = null

        internal val subtrieValues: List<Int>
            get() {
                return ((start ?: return emptyList())..(end ?: return emptyList())).toList()
            }

        internal fun addWord(word: String, value: Int) {
            var curNode = this
            curNode.updateSubtrie(value)

            for (char in word) {
                curNode = curNode.moveByChar(char, createNew = true)!!  // Can't be null when createNew is true
                curNode.updateSubtrie(value)
            }
            curNode.value = value
        }

        internal fun moveByChar(char: Char, createNew: Boolean = false): TrieNode? {
            if (!this.moves.containsKey(char)) {
                if (createNew) this.moves[char] = TrieNode()
                else return null
            }
            return this.moves.getValue(char)
        }

        private fun updateSubtrie(value: Int) {
            start = start?.let { min(it, value) } ?: value
            end = end?.let { max(it, value) } ?: value
        }
    }
}
