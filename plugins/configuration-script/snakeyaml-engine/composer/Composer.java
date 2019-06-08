/**
 * Copyright (c) 2018, http://www.snakeyaml.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.snakeyaml.engine.v1.composer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.events.AliasEvent;
import org.snakeyaml.engine.v1.events.Event;
import org.snakeyaml.engine.v1.events.MappingStartEvent;
import org.snakeyaml.engine.v1.events.NodeEvent;
import org.snakeyaml.engine.v1.events.ScalarEvent;
import org.snakeyaml.engine.v1.events.SequenceStartEvent;
import org.snakeyaml.engine.v1.exceptions.ComposerException;
import org.snakeyaml.engine.v1.exceptions.Mark;
import org.snakeyaml.engine.v1.nodes.MappingNode;
import org.snakeyaml.engine.v1.nodes.Node;
import org.snakeyaml.engine.v1.nodes.NodeTuple;
import org.snakeyaml.engine.v1.nodes.ScalarNode;
import org.snakeyaml.engine.v1.nodes.SequenceNode;
import org.snakeyaml.engine.v1.nodes.Tag;
import org.snakeyaml.engine.v1.parser.Parser;
import org.snakeyaml.engine.v1.resolver.ScalarResolver;


/**
 * Creates a node graph from parser events.
 * <p>
 * Corresponds to the 'Composer' step as described in chapter 3.1.2 of the
 * <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">YAML Specification</a>.
 * </p>
 */
public class Composer implements Iterator<Node> {
    protected final Parser parser;
    private final ScalarResolver scalarResolver;
    private final Map<Anchor, Node> anchors;
    private final Set<Node> recursiveNodes;

    public Composer(Parser parser, ScalarResolver scalarResolver) {
        this.parser = parser;
        this.scalarResolver = scalarResolver;
        this.anchors = new HashMap();
        this.recursiveNodes = new HashSet();
    }

    /**
     * Checks if further documents are available.
     *
     * @return <code>true</code> if there is at least one more document.
     */
    public boolean hasNext() {
        // Drop the STREAM-START event.
        if (parser.checkEvent(Event.ID.StreamStart)) {
            parser.next();
        }
        // If there are more documents available?
        return !parser.checkEvent(Event.ID.StreamEnd);
    }

    /**
     * Reads a document from a source that contains only one document.
     * <p>
     * If the stream contains more than one document an exception is thrown.
     * </p>
     *
     * @return The root node of the document or <code>Optional.empty()</code> if no document
     * is available.
     */
    public Optional<Node> getSingleNode() {
        // Drop the STREAM-START event.
        parser.next();
        // Compose a document if the stream is not empty.
        Optional<Node> document = Optional.empty();
        if (!parser.checkEvent(Event.ID.StreamEnd)) {
            document = Optional.of(next());
        }
        // Ensure that the stream contains no more documents.
        if (!parser.checkEvent(Event.ID.StreamEnd)) {
            Event event = parser.next();
            Optional<Mark> previousDocMark = document.flatMap(d -> d.getStartMark());
            throw new ComposerException("expected a single document in the stream", previousDocMark,
                    "but found another document", event.getStartMark());
        }
        // Drop the STREAM-END event.
        parser.next();
        return document;
    }

    /**
     * Reads and composes the next document.
     *
     * @return The root node of the document or <code>null</code> if no more
     * documents are available.
     */
    public Node next() {
        // Drop the DOCUMENT-START event.
        parser.next();
        // Compose the root node.
        Node node = composeNode(Optional.empty());
        // Drop the DOCUMENT-END event.
        parser.next();
        this.anchors.clear();
        recursiveNodes.clear();
        return node;
    }


    private Node composeNode(Optional<Node> parent) {
        parent.ifPresent(p -> recursiveNodes.add(p));//TODO add unit test for this line
        final Node node;
        if (parser.checkEvent(Event.ID.Alias)) {
            AliasEvent event = (AliasEvent) parser.next();
            Anchor anchor = event.getAnchor().get();
            if (!anchors.containsKey(anchor)) {
                throw new ComposerException("found undefined alias " + anchor, event.getStartMark());
            }
            node = anchors.get(anchor);
            if (recursiveNodes.remove(node)) {
                node.setRecursive(true);
            }
        } else {
            NodeEvent event = (NodeEvent) parser.peekEvent();
            Optional<Anchor> anchor = event.getAnchor();
            // the check for duplicate anchors has been removed (issue 174)
            if (parser.checkEvent(Event.ID.Scalar)) {
                node = composeScalarNode(anchor);
            } else if (parser.checkEvent(Event.ID.SequenceStart)) {
                node = composeSequenceNode(anchor);
            } else {
                node = composeMappingNode(anchor);
            }
        }
        parent.ifPresent(p -> recursiveNodes.remove(p));//TODO add unit test for this line
        return node;
    }

    private void registerAnchor(Anchor anchor, Node node) {
        anchors.put(anchor, node);
        node.setAnchor(Optional.of(anchor));
    }

    protected Node composeScalarNode(Optional<Anchor> anchor) {
        ScalarEvent ev = (ScalarEvent) parser.next();
        Optional<String> tag = ev.getTag();
        boolean resolved = false;
        Tag nodeTag;
        if (!tag.isPresent() || tag.get().equals("!")) {
            nodeTag = scalarResolver.resolve(ev.getValue(), ev.getImplicit().canOmitTagInPlainScalar());
            resolved = true;
        } else {
            nodeTag = new Tag(tag.get());
        }
        Node node = new ScalarNode(nodeTag, resolved, ev.getValue(), ev.getScalarStyle(), ev.getStartMark(), ev.getEndMark());
        anchor.ifPresent(a -> registerAnchor(a, node));
        return node;
    }

    protected Node composeSequenceNode(Optional<Anchor> anchor) {
        SequenceStartEvent startEvent = (SequenceStartEvent) parser.next();
        Optional<String> tag = startEvent.getTag();
        Tag nodeTag;
        boolean resolved = false;
        if (!tag.isPresent() || tag.get().equals("!")) {
            nodeTag = Tag.SEQ;
            resolved = true;
        } else {
            nodeTag = new Tag(tag.get());
        }
        final ArrayList<Node> children = new ArrayList();
        SequenceNode node = new SequenceNode(nodeTag, resolved, children, startEvent.getFlowStyle(), startEvent.getStartMark(),
                Optional.empty());
        anchor.ifPresent(a -> registerAnchor(a, node));
        while (!parser.checkEvent(Event.ID.SequenceEnd)) {
            children.add(composeNode(Optional.of(node)));
        }
        Event endEvent = parser.next();
        node.setEndMark(endEvent.getEndMark());
        return node;
    }

    protected Node composeMappingNode(Optional<Anchor> anchor) {
        MappingStartEvent startEvent = (MappingStartEvent) parser.next();
        Optional<String> tag = startEvent.getTag();
        Tag nodeTag;
        boolean resolved = false;
        if (!tag.isPresent() || tag.get().equals("!")) {
            nodeTag = Tag.MAP;
            resolved = true;
        } else {
            nodeTag = new Tag(tag.get());
        }

        final List<NodeTuple> children = new ArrayList<NodeTuple>();
        MappingNode node = new MappingNode(nodeTag, resolved, children, startEvent.getFlowStyle(), startEvent.getStartMark(), Optional.empty());
        anchor.ifPresent(a -> registerAnchor(a, node));
        while (!parser.checkEvent(Event.ID.MappingEnd)) {
            composeMappingChildren(children, node);
        }
        Event endEvent = parser.next();
        node.setEndMark(endEvent.getEndMark());
        return node;
    }

    protected void composeMappingChildren(List<NodeTuple> children, MappingNode node) {
        Node itemKey = composeKeyNode(node);
        if (itemKey.getTag().equals(Tag.MERGE)) {
            node.setMerged(true);
        }
        Node itemValue = composeValueNode(node);
        children.add(new NodeTuple(itemKey, itemValue));
    }

    protected Node composeKeyNode(MappingNode node) {
        return composeNode(Optional.of(node));
    }

    protected Node composeValueNode(MappingNode node) {
        return composeNode(Optional.of(node));
    }
}
