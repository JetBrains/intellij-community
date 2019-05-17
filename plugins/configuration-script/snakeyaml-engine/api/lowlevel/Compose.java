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

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import org.snakeyaml.engine.v1.api.LoadSettings;
import org.snakeyaml.engine.v1.api.YamlUnicodeReader;
import org.snakeyaml.engine.v1.composer.Composer;
import org.snakeyaml.engine.v1.nodes.Node;
import org.snakeyaml.engine.v1.parser.ParserImpl;
import org.snakeyaml.engine.v1.scanner.StreamReader;

public class Compose {

    private LoadSettings settings;

    /**
     * Create instance with provided {@link LoadSettings}
     *
     * @param settings - configuration
     */
    public Compose(LoadSettings settings) {
        Objects.requireNonNull(settings, "LoadSettings cannot be null");
        this.settings = settings;
    }

    /**
     * Parse a YAML stream and produce {@link Node}
     *
     * @param yaml - YAML document(s). Since the encoding is already known the BOM must not be present (it will be parsed as content)
     * @return parsed {@link Node} if available
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Optional<Node> composeReader(Reader yaml) {
        Objects.requireNonNull(yaml, "Reader cannot be null");
        return new Composer(new ParserImpl(new StreamReader(yaml, settings), settings), settings.getScalarResolver()).getSingleNode();
    }

    /**
     * Parse a YAML stream and produce {@link Node}
     *
     * @param yaml - YAML document(s). Default encoding is UTF-8. The BOM must be present if the encoding is UTF-16 or UTF-32
     * @return parsed {@link Node} if available
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Optional<Node> composeInputStream(InputStream yaml) {
        Objects.requireNonNull(yaml, "InputStream cannot be null");
        return new Composer(new ParserImpl(new StreamReader(new YamlUnicodeReader(yaml), settings), settings), settings.getScalarResolver()).getSingleNode();
    }

    /**
     * Parse a YAML stream and produce {@link Node}
     *
     * @param yaml - YAML document(s).
     * @return parsed {@link Node} if available
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Optional<Node> composeString(String yaml) {
        Objects.requireNonNull(yaml, "String cannot be null");
        return new Composer(new ParserImpl(new StreamReader(new StringReader(yaml), settings), settings), settings.getScalarResolver()).getSingleNode();
    }

    // Compose all documents

    /**
     * Parse all YAML documents in a stream and produce corresponding representation trees.
     *
     * @param yaml stream of YAML documents
     * @return parsed root Nodes for all the specified YAML documents
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Iterable<Node> composeAllFromReader(Reader yaml) {
        Objects.requireNonNull(yaml, "Reader cannot be null");
        return () -> new Composer(new ParserImpl(new StreamReader(yaml, settings), settings), settings.getScalarResolver());
    }

    /**
     * Parse all YAML documents in a stream and produce corresponding representation trees.
     *
     * @param yaml - YAML document(s). Default encoding is UTF-8. The BOM must be present if the encoding is UTF-16 or UTF-32
     * @return parsed root Nodes for all the specified YAML documents
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Iterable<Node> composeAllFromInputStream(InputStream yaml) {
        Objects.requireNonNull(yaml, "InputStream cannot be null");
        return () -> new Composer(new ParserImpl(new StreamReader(new YamlUnicodeReader(yaml), settings), settings), settings.getScalarResolver());
    }

    /**
     * Parse all YAML documents in a stream and produce corresponding representation trees.
     *
     * @param yaml - YAML document(s).
     * @return parsed root Nodes for all the specified YAML documents
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2762107">Processing Overview</a>
     */
    public Iterable<Node> composeAllFromString(String yaml) {
        Objects.requireNonNull(yaml, "String cannot be null");
        //do not use lambda to keep Iterable and Iterator visible
        return new Iterable() {
            public Iterator<Node> iterator() {
                return new Composer(new ParserImpl(
                        new StreamReader(new StringReader(yaml), settings), settings), settings.getScalarResolver());
            }
        };
    }
}




