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
package org.snakeyaml.engine.v1.serializer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.snakeyaml.engine.v1.api.DumpSettings;
import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.emitter.Emitable;
import org.snakeyaml.engine.v1.events.AliasEvent;
import org.snakeyaml.engine.v1.events.DocumentEndEvent;
import org.snakeyaml.engine.v1.events.DocumentStartEvent;
import org.snakeyaml.engine.v1.events.ImplicitTuple;
import org.snakeyaml.engine.v1.events.MappingEndEvent;
import org.snakeyaml.engine.v1.events.MappingStartEvent;
import org.snakeyaml.engine.v1.events.ScalarEvent;
import org.snakeyaml.engine.v1.events.SequenceEndEvent;
import org.snakeyaml.engine.v1.events.SequenceStartEvent;
import org.snakeyaml.engine.v1.events.StreamEndEvent;
import org.snakeyaml.engine.v1.events.StreamStartEvent;
import org.snakeyaml.engine.v1.nodes.AnchorNode;
import org.snakeyaml.engine.v1.nodes.CollectionNode;
import org.snakeyaml.engine.v1.nodes.MappingNode;
import org.snakeyaml.engine.v1.nodes.Node;
import org.snakeyaml.engine.v1.nodes.NodeTuple;
import org.snakeyaml.engine.v1.nodes.NodeType;
import org.snakeyaml.engine.v1.nodes.ScalarNode;
import org.snakeyaml.engine.v1.nodes.SequenceNode;
import org.snakeyaml.engine.v1.nodes.Tag;

public class Serializer {
    private final DumpSettings settings;
    private final Emitable emitable;
    private Set<Node> serializedNodes;
    private Map<Node, Anchor> anchors;

    public Serializer(DumpSettings settings, Emitable emitable) {
        this.settings = settings;
        this.emitable = emitable;
        this.serializedNodes = new HashSet();
        this.anchors = new HashMap();
    }

    public void serialize(Node node) {
        this.emitable.emit(new DocumentStartEvent(settings.isExplicitStart(), settings.getYamlDirective(), settings.getTagDirective()));
        anchorNode(node);
        settings.getExplicitRootTag().ifPresent(tag -> node.setTag(tag));
        serializeNode(node, Optional.empty());
        this.emitable.emit(new DocumentEndEvent(settings.isExplicitEnd()));
        this.serializedNodes.clear();
        this.anchors.clear();
    }

    public void open() {
        this.emitable.emit(new StreamStartEvent());
    }

    public void close() {
        this.emitable.emit(new StreamEndEvent());
    }

    private void anchorNode(Node node) {
        if (node.getNodeType() == NodeType.ANCHOR) {
            node = ((AnchorNode) node).getRealNode();
        }
        if (this.anchors.containsKey(node)) {
            Anchor anchor = this.anchors.get(node);
            if (null == anchor) {
                anchor = settings.getAnchorGenerator().nextAnchor(node);
                this.anchors.put(node, anchor);
            }
        } else {
            this.anchors.put(node, null);
            switch (node.getNodeType()) {
                case SEQUENCE:
                    SequenceNode seqNode = (SequenceNode) node;
                    List<Node> list = seqNode.getValue();
                    for (Node item : list) {
                        anchorNode(item);
                    }
                    break;
                case MAPPING:
                    MappingNode mnode = (MappingNode) node;
                    List<NodeTuple> map = mnode.getValue();
                    for (NodeTuple object : map) {
                        Node key = object.getKeyNode();
                        Node value = object.getValueNode();
                        anchorNode(key);
                        anchorNode(value);
                    }
                    break;
            }
        }
    }

    // parent is not used
    private void serializeNode(Node node, Optional<Node> parent) {
        if (node.getNodeType() == NodeType.ANCHOR) {
            node = ((AnchorNode) node).getRealNode();
        }
        Optional<Anchor> tAlias = Optional.ofNullable(this.anchors.get(node));
        if (this.serializedNodes.contains(node)) {
            this.emitable.emit(new AliasEvent(tAlias));
        } else {
            this.serializedNodes.add(node);
            switch (node.getNodeType()) {
                case SCALAR:
                    ScalarNode scalarNode = (ScalarNode) node;
                    Tag detectedTag = settings.getScalarResolver().resolve(scalarNode.getValue(), true);
                    Tag defaultTag = settings.getScalarResolver().resolve(scalarNode.getValue(), false);
                    ImplicitTuple tuple = new ImplicitTuple(node.getTag().equals(detectedTag), node
                            .getTag().equals(defaultTag));
                    ScalarEvent event = new ScalarEvent(tAlias, Optional.of(node.getTag().getValue()), tuple,
                            scalarNode.getValue(), scalarNode.getScalarStyle());
                    this.emitable.emit(event);
                    break;
                case SEQUENCE:
                    SequenceNode seqNode = (SequenceNode) node;
                    boolean implicitS = node.getTag().equals(Tag.SEQ);
                    this.emitable.emit(new SequenceStartEvent(tAlias, Optional.of(node.getTag().getValue()),
                            implicitS, seqNode.getFlowStyle()));
                    List<Node> list = seqNode.getValue();
                    for (Node item : list) {
                        serializeNode(item, Optional.of(node));
                    }
                    this.emitable.emit(new SequenceEndEvent());
                    break;
                default:// instance of MappingNode
                    boolean implicitM = node.getTag().equals(Tag.MAP);
                    this.emitable.emit(new MappingStartEvent(tAlias, Optional.of(node.getTag().getValue()),
                            implicitM, ((CollectionNode) node).getFlowStyle()));
                    MappingNode mappingNode = (MappingNode) node;
                    List<NodeTuple> map = mappingNode.getValue();
                    for (NodeTuple entry : map) {
                        Node key = entry.getKeyNode();
                        Node value = entry.getValueNode();
                        serializeNode(key, Optional.of(mappingNode));
                        serializeNode(value, Optional.of(mappingNode));
                    }
                    this.emitable.emit(new MappingEndEvent());
            }
        }
    }
}
