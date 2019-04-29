package com.intellij.configurationScript

import gnu.trove.THashMap
import gnu.trove.THashSet
import org.yaml.snakeyaml.composer.ComposerException
import org.yaml.snakeyaml.error.Mark
import org.yaml.snakeyaml.events.*
import org.yaml.snakeyaml.nodes.*
import org.yaml.snakeyaml.parser.Parser
import java.util.*

internal class LightweightComposer(private val parser: Parser) {
  private val anchors = THashMap<String, Node>()
  private val recursiveNodes = THashSet<Node>()

  /**
   * Reads and composes the next document.
   *
   * @return The root node of the document or `null` if no more
   * documents are available.
   */
  // Drop the DOCUMENT-START event.
  // Compose the root node.
  // Drop the DOCUMENT-END event.
  private fun readNode(): Node {
    parser.event
    val node = composeNode(null)
    parser.event
    anchors.clear()
    recursiveNodes.clear()
    return node
  }

  /**
   * Reads a document from a source that contains only one document.
   *
   *
   * If the stream contains more than one document an exception is thrown.
   *
   *
   * @return The root node of the document or `null` if no document
   * is available.
   */
  // Drop the STREAM-START event.
  // Compose a document if the stream is not empty.
  // Ensure that the stream contains no more documents.
  // Drop the STREAM-END event.
  fun getSingleNode(): Node? {
    parser.event
    var document: Node? = null
    if (!parser.checkEvent(Event.ID.StreamEnd)) {
      document = readNode()
    }
    if (!parser.checkEvent(Event.ID.StreamEnd)) {
      val event = parser.event
      throw MyComposerException("expected a single document in the stream",
                                document!!.startMark, "but found another document", event.startMark)
    }
    parser.event
    return document
  }

  private fun composeNode(parent: Node?): Node {
    if (parent != null) {
      recursiveNodes.add(parent)
    }

    val event = parser.peekEvent()
    val node: Node
    if (event is AliasEvent) {
      val anchor = event.anchor
      node = anchors.get(anchor) ?: throw MyComposerException(null, null, "found undefined alias $anchor", event.startMark)
      if (recursiveNodes.remove(node)) {
        node.isTwoStepsConstruction = true
      }
    }
    else {
      val anchor = (event as NodeEvent).anchor
      // the check for duplicate anchors has been removed (issue 174)
      node = when {
        event.`is`(Event.ID.Scalar) -> composeScalarNode(anchor)
        event.`is`(Event.ID.SequenceStart) -> composeSequenceNode(anchor)
        else -> composeMappingNode(anchor)
      }
    }
    recursiveNodes.remove(parent)
    return node
  }

  private fun composeScalarNode(anchor: String?): Node {
    val event = parser.event as ScalarEvent
    // tag cannot be null, use STR
    val node = ScalarNode(Tag.STR, false, event.value, null, null, event.style)
    if (anchor != null) {
      anchors.set(anchor, node)
    }
    return node
  }

  private fun composeSequenceNode(anchor: String?): Node {
    val startEvent = parser.event as SequenceStartEvent
    val children = ArrayList<Node>()
    val node = SequenceNode(Tag.SEQ, false, children, null, null, startEvent.flowStyle)
    if (anchor != null) {
      anchors.put(anchor, node)
    }
    while (!parser.checkEvent(Event.ID.SequenceEnd)) {
      children.add(composeNode(node))
    }
    // remove end event
    parser.event
    return node
  }

  private fun composeMappingNode(anchor: String?): Node {
    val startEvent = parser.event as MappingStartEvent
    val children = ArrayList<NodeTuple>()
    val node = MappingNode(Tag.MAP, false, children, null, null, startEvent.flowStyle)
    if (anchor != null) {
      anchors.put(anchor, node)
    }

    while (!parser.checkEvent(Event.ID.MappingEnd)) {
      composeMappingChildren(children, node)
    }
    // remove end event
    parser.event
    return node
  }

  private fun composeMappingChildren(children: MutableList<NodeTuple>, node: MappingNode) {
    val itemKey = composeNode(node)
    if (itemKey.tag == Tag.MERGE) {
      node.isMerged = true
    }
    val itemValue = composeNode(node)
    children.add(NodeTuple(itemKey, itemValue))
  }
}

private class MyComposerException(context: String?, contextMark: Mark?, problem: String, problemMark: Mark) :
  ComposerException(context, contextMark, problem, problemMark)