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
package org.snakeyaml.engine.v1.api;

import java.io.StringWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import org.snakeyaml.engine.v1.emitter.Emitter;
import org.snakeyaml.engine.v1.nodes.Node;
import org.snakeyaml.engine.v1.representer.BaseRepresenter;
import org.snakeyaml.engine.v1.representer.StandardRepresenter;
import org.snakeyaml.engine.v1.serializer.Serializer;

/**
 * Common way to serialize any Java instance(s)
 */
public class Dump {

    private DumpSettings settings;
    private BaseRepresenter representer;

    /**
     * Create instance
     *
     * @param settings - configuration
     */
    public Dump(DumpSettings settings) {
        this(settings, new StandardRepresenter(settings));
    }

    /**
     * Create instance
     * @param settings - configuration
     * @param representer - custom representer
     */
    public Dump(DumpSettings settings, BaseRepresenter representer) {
        Objects.requireNonNull(settings, "DumpSettings cannot be null");
        Objects.requireNonNull(representer, "Representer cannot be null");
        this.settings = settings;
        this.representer = representer;
    }

    /**
     * Dump all the instances from the iterator into a stream with every instance in a separate YAML document
     *
     * @param instancesIterator - instances to serialize
     * @param streamDataWriter  - destination I/O writer
     */
    public void dumpAll(Iterator<? extends Object> instancesIterator, StreamDataWriter streamDataWriter) {
        Serializer serializer = new Serializer(settings, new Emitter(settings, streamDataWriter));
        serializer.open();
        while (instancesIterator.hasNext()) {
            Object instance = instancesIterator.next();
            Node node = representer.represent(instance);
            serializer.serialize(node);
        }
        serializer.close();
    }

    /**
     * Dump a single instance into a YAML document
     *
     * @param yaml             - instance to serialize
     * @param streamDataWriter - destination I/O writer
     */
    public void dump(Object yaml, StreamDataWriter streamDataWriter) {
        Iterator<? extends Object> iter = Collections.singleton(yaml).iterator();
        dumpAll(iter, streamDataWriter);
    }

    /**
     * Dump all the instances from the iterator into a stream with every instance in a separate YAML document
     *
     * @param instancesIterator - instances to serialize
     * @return String representation of the YAML stream
     */
    public String dumpAllToString(Iterator<? extends Object> instancesIterator) {
        StreamToStringWriter writer = new StreamToStringWriter();
        dumpAll(instancesIterator, writer);
        return writer.toString();
    }

    /**
     * Dump all the instances from the iterator into a stream with every instance in a separate YAML document
     *
     * @param yaml - instance to serialize
     * @return String representation of the YAML stream
     */
    public String dumpToString(Object yaml) {
        StreamToStringWriter writer = new StreamToStringWriter();
        dump(yaml, writer);
        return writer.toString();
    }
}

class StreamToStringWriter extends StringWriter implements StreamDataWriter {
}




