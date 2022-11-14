package org.jetbrains.completion.full.line.local.tokenizer

import java.io.File
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.min

// TODO: use different types to prevent overflow
// uint32_t -> Int
// uint64_t -> Long
// flat_hash_map -> HashMap / Map
// vector -> ArrayList / List
// std::ifstream -> java.util.Scanner

fun int2comb(a: Int, b: Int): Long {
  return (a.toLong() shl 32) + b.toLong()
}

fun isSpace(ch: Int): Boolean {
  // TODO: why do we need two
  return ch == '\n'.toInt() || ch == SPACE_TOKEN
}

fun token2word(source: List<Int>, id2char: Map<Int, Int>): String {
  // TODO: check for utf8
  return String(source.map {
    id2char.getValue(it).toChar()
  }.toCharArray())
}

fun <T> concatVectors(vararg lists: List<T>): List<T> {
  return listOf(*lists).flatten()
}

const val SPACE_TOKEN = 9601
const val UNK_TOKEN = "<UNK>"
const val PAD_TOKEN = "<PAD>"
const val BOS_TOKEN = "<BOS>"
const val EOS_TOKEN = "<EOS>"

data class Status(
  var code: Int, var message: String
) {
  fun ok(): Boolean {
    return code == 0
  }

  constructor() : this(0, "")
}

data class BPERule(
  // x + y -> z
  val x: Int = 0, val y: Int = 0, val z: Int = 0
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as BPERule
    return x == other.x && y == other.y && z == other.z
  }

  override fun hashCode(): Int {
    var result = x
    result = 31 * result + y
    result = 31 * result + z
    return result
  }
}

data class BPEState(
  var char2id: HashMap<Int, Int>, var rules: ArrayList<BPERule>, var specialTokens: SpecialTokens
) {
  constructor() : this(HashMap(), ArrayList(), SpecialTokens())


  fun load(file: File): Status {
    // TODO: check if it's valid to use constructors instead of clear()
    char2id = HashMap()
    rules = ArrayList()
    val scanner = Scanner(file)
    val n = scanner.nextInt()
    val m = scanner.nextInt()
    for (i in 0 until n) {
      val innerId = scanner.nextInt()
      val utf32Id = scanner.nextInt()
      char2id[innerId] = utf32Id
    }

    // TODO: micro-optimization available here, allocating arraylist beforehand
    for (i in 0 until m) {
      val x = scanner.nextInt()
      val y = scanner.nextInt()
      val z = scanner.nextInt()
      rules.add(BPERule(x, y, z))
    }

    specialTokens.load(scanner)
    scanner.close()
    return Status()
  }
}

data class SpecialTokens(
  var padId: Int = -1,
  var unkId: Int = -1,
  var bosId: Int = -1,
  var eosId: Int = -1,
) {
  fun load(scanner: Scanner) {
    unkId = scanner.nextInt()
    padId = scanner.nextInt()
    bosId = scanner.nextInt()
    eosId = scanner.nextInt()
  }

  fun nSpecialTokens(): Int {
    var cnt = 0
    cnt += (unkId != -1).compareTo(false)
    cnt += (padId != -1).compareTo(false)
    cnt += (bosId != -1).compareTo(false)
    cnt += (eosId != -1).compareTo(false)
    return cnt
  }
}

class FullLineTokenizer(modelFile: File, nThreads: Int) : Tokenizer {
  private var encoder = BaseEncoder(modelFile, nThreads)
  override val vocabSize = encoder.vocabSize()
  override val eosTokenId = this.encoder.bpeState.specialTokens.eosId
  override val invalidIds: Set<Int> = setOf(
    this.encoder.bpeState.specialTokens.unkId,
    this.encoder.bpeState.specialTokens.padId,
    this.encoder.bpeState.specialTokens.bosId,
    this.encoder.bpeState.specialTokens.eosId
  )

  // A copy of BPE.encode() from yttm.pyx
  fun encode(
    sentences: List<String>,
    bos: Boolean = false,
    eos: Boolean = false,
    reverse: Boolean = false,
    dropoutProb: Double = 0.0
  ): List<List<Int>> {

    if (dropoutProb < 0 || dropoutProb > 1) {
      throw IllegalArgumentException(
        "dropoutProb value must be in the range [0, 1]. Current value of dropoutProb = $dropoutProb"
      )
    }

    // Output type is ids
    // We had to convert string to bytes in Cython, I don't know yet if we should do it here
    // val s = sentences.map {
    //     it.toByteArray()
    // }
    val encodingResult = encoder.encodeAsIds(sentences, bos, eos, reverse, dropoutProb)
    if (encodingResult.status.code != 0 || encodingResult.ids == null) throw IllegalArgumentException(encodingResult.status.message)
    return encodingResult.ids
  }

  fun decode(ids: List<List<Int>>): List<String> {
    return encoder.decodeIds(ids, null).sentences
  }

  fun encode(
    sentence: String,
    bos: Boolean = false,
    eos: Boolean = false,
    reverse: Boolean = false,
    dropoutProb: Double = 0.0
  ): List<Int> {
    return this.encode(listOf(sentence), bos, eos, reverse, dropoutProb)[0]
  }

  fun decode(ids: List<Int>): String {
    return this.decode(listOf(ids))[0]
  }

  override fun encode(sentences: List<String>): List<IntArray> {
    return encode(
      sentences, bos = false, eos = false, reverse = false, dropoutProb = 0.0
    ).map { it.toIntArray() }
  }

  override fun encode(sentence: String): IntArray {
    return encode(sentence, bos = false, eos = false, reverse = false, dropoutProb = 0.0).toIntArray()
  }

  override fun decode(ids: IntArray): String {
    return decode(listOf(ids.toList()))[0]
  }

  override fun decode(ids: IntArray, separator: String): String {
    return ids.map { decode(it) }.joinToString(separator)
  }

  override fun decode(id: Int): String {
    return encoder.idToSubword(id, true)
  }

  override fun isValidString(s: String): Boolean {
    return true
  }
  
  override fun idsByRegex(regex: Regex): Set<Int> {
    return vocab.filterKeys { it.contains(regex) }.values.toSet()
  }

  override val vocab: Map<String, Int> = encoder.vocabulary().mapIndexed { index, s -> Pair(s, index) }.toMap()
}


// TODO: use better hashmap (or maybe this is good enough)
class BaseEncoder(modelFile: File, private var nThreads: Int) {
  // BPEState bpeState;
  internal var bpeState: BPEState = BPEState()

  // flat_hash_map<uint32_t, uint32_t> id2char;
  private var id2char = HashMap<Int, Int>()

  // flat_hash_map<uint64_t, int> rule2id;
  private var rule2id = HashMap<Long, Int>()

  // flat_hash_map<uint32_t, std::vector<uint32_t>> recipe;
  private var recipe = HashMap<Int, List<Int>>()

  // flat_hash_map<std::string, uint32_t> reversed_recipe;
  private var reversedRecipe = HashMap<String, Int>()


  init {
    assert(nThreads >= 1 || nThreads == -1)
    if (nThreads == -1) {
      // TODO: determine hardware concurrency
      // nThreads = max(1, int(std::thread::hardwareConcurrency()));
      this.nThreads = 2
    }

    val status: Status = bpeState.load(modelFile)
    if (!status.ok()) {
      throw BPEException("Couldn't load model from ${modelFile.absolutePath}")
    }
    this.fillFromState()
  }


  private fun fillFromState() {
    bpeState.char2id.map { it.value to it.key }.toMap(id2char)
    bpeState.rules.mapIndexed { i, rule -> int2comb(rule.x, rule.y) to i }.toMap(rule2id)
    id2char.map { it.key to listOf(it.key) }.toMap(recipe)
    bpeState.rules.forEach {
      recipe[it.z] = concatVectors(recipe[it.x] ?: error(it), recipe[it.y] ?: error(it))
    }
    recipe.map { token2word(it.value, id2char) to it.key }.toMap(reversedRecipe)
    reversedRecipe[BOS_TOKEN] = bpeState.specialTokens.bosId
    reversedRecipe[EOS_TOKEN] = bpeState.specialTokens.eosId
  }

  data class EncodingResult(val status: Status, val ids: List<List<Int>>?)

  data class DecodingResult(val status: Status, val sentences: List<String>)

  data class EncodingConfig(val bos: Boolean, val eos: Boolean, val reverse: Boolean, val dropoutProb: Double)

  // BaseEncoder::encode_as_ids()
  fun encodeAsIds(
    sentences: List<String>, bos: Boolean, eos: Boolean, reverse: Boolean, dropoutProb: Double
  ): EncodingResult {
    val encodingConfig = EncodingConfig(bos, eos, reverse, dropoutProb)
    val encodingParallelResult = encodeParallel(sentences, encodingConfig)
    val decodeResults = encodingParallelResult.ids
    val status = encodingParallelResult.status
    return EncodingResult(status, decodeResults)
  }

  // BaseEncoder::encode_parallel()
  private fun encodeParallel(
    sentences: List<String>, encodingConfig: EncodingConfig
  ): EncodingResult {
    if (encodingConfig.bos && bpeState.specialTokens.bosId == -1) {
      return EncodingResult(
        Status(1, "Can't add <BOS> token. Model was trained without it."), null
      )
    }
    if (encodingConfig.eos && bpeState.specialTokens.eosId == -1) {
      return EncodingResult(
        Status(1, "Can't add <EOS> token. Model was trained without it."), null
      )
    }

    val ids = arrayOfNulls<List<Int>>(sentences.size).asList().toMutableList()
    if (sentences.size <= nThreads * 3 || nThreads == 1) {
      // Not too many sentences. It's better to solve it without threads.
      sentences.forEachIndexed { index, sentence ->
        ids[index] = encodeSentence(sentence, encodingConfig)
      }
    }
    else {
      val threads: List<Thread> = (0..nThreads).map {
        thread {
          val tasksForThread: Int = ((sentences.size + nThreads - 1) / nThreads)
          val firstTask: Int = tasksForThread * it
          val lastTask: Int = min(tasksForThread * (it + 1), sentences.size - 1)
          (firstTask..lastTask).forEach {
            ids[it] = encodeSentence(sentences[it], encodingConfig)
          }
        }

      }

      threads.forEach {
        it.join()
      }
    }

    assert(ids.none { it == null })
    return EncodingResult(
      Status(), ids.filterNotNull()
    )
  }

  private fun encodeSentence(sentence: String, encodingConfig: EncodingConfig): List<Int> {
    data class NodeDecoder(
      var tokenId: Int, var prev: Int, var next: Int
    ) {
      constructor (tokenId: Int, curPos: Int) : this(tokenId, curPos - 1, curPos + 1)
    }

    data class MergeEvent2(
      val priority: Int, val pos: Int
    ) : Comparable<MergeEvent2> {
      override fun compareTo(other: MergeEvent2): Int {
        return if (priority != other.priority) {
          priority - other.priority
        }
        else {
          pos - other.pos
        }
      }
    }

    // vector<int> output_ids;
    val outputIds = ArrayList<Int>()

    if (encodingConfig.bos) {
      outputIds.add(bpeState.specialTokens.bosId)
    }

    // vector<NodeDecoder> list;
    val list: MutableList<NodeDecoder> = ArrayList()

    // flat_hash_map<uint32_t, string> unrecognized_tokens;
    val unrecognizedTokens = HashMap<Int, String>()

    val text: List<Int> = sentence.map { it.toInt() }

    // TODO: why do we need to assert in each encoding
    assert(bpeState.char2id.keys.any { it == SPACE_TOKEN })


    //        TODO: trim trailing separators, why commented?
    //        while (text.isNotEmpty() && isSpace(text.last())){
    //            text = text.subList(0, text.size)
    //        }

    // TODO: may overflow
    val newTokensStart = 1e9.toInt() // just some number that bigger than any subword id
    var newTokenCur: Int

    var textIndex = 0
    while (textIndex < text.size) {
      list.clear()
      unrecognizedTokens.clear()

      while (textIndex < text.size && isSpace(text[textIndex])) {
        textIndex++
      }

      val beginOfWordIndex = textIndex

      while (textIndex < text.size && !isSpace(text[textIndex])) {
        textIndex++
      }

      val endOfWordIndex = textIndex

      newTokenCur = newTokensStart
      list.add(NodeDecoder(bpeState.char2id.getValue(SPACE_TOKEN), 0))

      var charInWordIndex = beginOfWordIndex
      while (charInWordIndex < endOfWordIndex) {
        if (!bpeState.char2id.containsKey(text[charInWordIndex])) {
          val unrecognizedBeginIndex = charInWordIndex
          while (charInWordIndex < endOfWordIndex && !bpeState.char2id.containsKey(text[charInWordIndex])) {
            charInWordIndex++
          }

          unrecognizedTokens[newTokenCur] =
            text.slice(IntRange(unrecognizedBeginIndex, charInWordIndex - 1)).map { it.toByte() }
              .toByteArray().toString(Charset.forName("UTF-8"))

          list.add(NodeDecoder(newTokenCur, list.size))
          newTokenCur++
        }
        else {
          list.add(NodeDecoder(bpeState.char2id.getValue(text[charInWordIndex]), list.size))
          charInWordIndex++
        }
      }
      list.last().next = -1

      val pairCode = { firstPos: Int ->
        val secondPos = list[firstPos].next
        // TODO: correct types
        int2comb(list[firstPos].tokenId, list[secondPos].tokenId)
      }

      val queue: BasePriorityQueue<MergeEvent2> = if (encodingConfig.dropoutProb == 0.0) {
        STLQueue()
      }
      else {
        DropoutQueue(encodingConfig.dropoutProb)
      }

      val pushInQueueIfRuleExists = { pos: Int ->
        val id = rule2id[pairCode(pos)]
        if (id != null) {
          queue.push(
            MergeEvent2(
              id, pos
            )
          )
        }
      }

      for (i in 0..list.size - 2) pushInQueueIfRuleExists(i)

      while (true) {
        val event: MergeEvent2 = queue.pop() ?: break
        val ruleId = event.priority
        val pos1 = event.pos
        val pos2 = list[pos1].next
        assert(pos1 != pos2)
        if (list[pos1].tokenId != bpeState.rules[ruleId].x || pos2 == -1 || list[pos2].tokenId != bpeState.rules[ruleId].y) {
          continue
        }

        val pos0 = list[pos1].prev
        val pos3 = list[pos2].next

        list[pos2] = NodeDecoder(0, -1, -1)
        list[pos1] = NodeDecoder(bpeState.rules[ruleId].z, pos0, pos3)
        if (pos3 != -1) {
          list[pos3].prev = pos1
        }

        if (pos0 != -1) {
          pushInQueueIfRuleExists(pos0)
        }
        if (pos3 != -1) {
          pushInQueueIfRuleExists(pos1)
        }
      }

      var aliveTokenIndex = list.indexOf(list.find {
        it.tokenId != 0
      })
      assert(aliveTokenIndex != -1)
      while (aliveTokenIndex != -1) {
        val tokenId = list[aliveTokenIndex].tokenId
        if (tokenId >= newTokensStart) {
          outputIds.add(bpeState.specialTokens.unkId)
        }
        else {
          outputIds.add(tokenId)
        }
        aliveTokenIndex = list[aliveTokenIndex].next
      }
    }
    return outputIds
  }

  fun decodeIds(
    ids: List<List<Int>>, ignoreIds: Set<Int>?
  ): DecodingResult {
    val sentences = ArrayList<String>()
    for (sentenceIds in ids) {
      val sentence = decodeSentence(sentenceIds, ignoreIds)
      sentences.add(sentence)
    }
    return DecodingResult(
      Status(), sentences
    )
  }

  private fun decodeSentence(sentenceIds: List<Int>, ignoreIds: Set<Int>?): String {
    var sentence = ""
    //        var firstIter = true
    for (id in sentenceIds) {
      if ((ignoreIds != null) || (ignoreIds?.contains(id) != false)) {
        sentence += idToSubword(id, true)
        //                if (firstIter && sentence[0] == '\n') {
        //                    sentence = sentence.substring(1)
        //                }
        //                firstIter = false
      }
    }
    return sentence
  }

  internal fun vocabSize(): Int {
    return bpeState.rules.size + bpeState.char2id.size + bpeState.specialTokens.nSpecialTokens()
  }

  internal fun idToSubword(id: Int, replaceSpace: Boolean): String {
    if (id < 0 || vocabSize() <= id) {
      throw IllegalArgumentException(
        "id must be in the range [0, vocab_size - 1]. Current value: vocab_size=${vocabSize()}; id=$id;"
      )
    }

    if (bpeState.specialTokens.unkId == id) return UNK_TOKEN

    if (bpeState.specialTokens.padId == id) return PAD_TOKEN

    // TODO: test bos and eos behavior
    if (bpeState.specialTokens.bosId == id) return BOS_TOKEN

    if (bpeState.specialTokens.eosId == id) return EOS_TOKEN


    assert(recipe.containsKey(id))
    if (replaceSpace) {
      val symbols = recipe.getValue(id)
      if (id2char.getValue(symbols[0]) == SPACE_TOKEN) {
        return "\n" + token2word(symbols.subList(1, symbols.size), id2char)
      }
    }
    return token2word(recipe.getValue(id), id2char)
  }

  internal fun vocabulary(): List<String> {
    return List(vocabSize()) {
      idToSubword(it, true)
    }
  }
}

class BPEException(s: String) : Exception(s)
