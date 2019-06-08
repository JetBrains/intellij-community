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
package org.snakeyaml.engine.v1.api.lowlevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.snakeyaml.engine.v1.api.DumpSettings;
import org.snakeyaml.engine.v1.emitter.Emitable;
import org.snakeyaml.engine.v1.events.Event;
import org.snakeyaml.engine.v1.nodes.Node;
import org.snakeyaml.engine.v1.serializer.Serializer;

public class Serialize {

    private final DumpSettings settings;

    /**
     * Create instance with provided {@link DumpSettings}
     *
     * @param settings - configuration
     */
    public Serialize(DumpSettings settings) {
        Objects.requireNonNull(settings, "DumpSettings cannot be null");
        this.settings = settings;
    }

    /**
     * Serialize a {@link Node} and produce events.
     *
     * @param node - {@link Node} to serialize
     * @return serialized events
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public List<Event> serializeOne(Node node) {
        Objects.requireNonNull(node, "Node cannot be null");
        return serializeAll(Collections.singletonList(node));
    }

    /**
     * Serialize {@link Node}s and produce events.
     *
     * @param nodes - {@link Node}s to serialize
     * @return serialized events
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public List<Event> serializeAll(List<Node> nodes) {
        Objects.requireNonNull(nodes, "Nodes cannot be null");
        Events events = new Events();
        Serializer serializer = new Serializer(settings, events);
        serializer.open();
        for (Node node : nodes) {
            serializer.serialize(node);
        }
        serializer.close();
        return events.getEvents();
    }
}

class Events implements Emitable {
    private List<Event> events = new ArrayList<>();

    @Override
    public void emit(Event event) {
        events.add(event);
    }

    public List<Event> getEvents() {
        return events;
    }
}

