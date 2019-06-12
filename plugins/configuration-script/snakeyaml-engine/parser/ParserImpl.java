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
package org.snakeyaml.engine.v1.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.snakeyaml.engine.v1.api.LoadSettings;
import org.snakeyaml.engine.v1.common.Anchor;
import org.snakeyaml.engine.v1.common.ArrayStack;
import org.snakeyaml.engine.v1.common.FlowStyle;
import org.snakeyaml.engine.v1.common.ScalarStyle;
import org.snakeyaml.engine.v1.common.SpecVersion;
import org.snakeyaml.engine.v1.events.AliasEvent;
import org.snakeyaml.engine.v1.events.DocumentEndEvent;
import org.snakeyaml.engine.v1.events.DocumentStartEvent;
import org.snakeyaml.engine.v1.events.Event;
import org.snakeyaml.engine.v1.events.ImplicitTuple;
import org.snakeyaml.engine.v1.events.MappingEndEvent;
import org.snakeyaml.engine.v1.events.MappingStartEvent;
import org.snakeyaml.engine.v1.events.ScalarEvent;
import org.snakeyaml.engine.v1.events.SequenceEndEvent;
import org.snakeyaml.engine.v1.events.SequenceStartEvent;
import org.snakeyaml.engine.v1.events.StreamEndEvent;
import org.snakeyaml.engine.v1.events.StreamStartEvent;
import org.snakeyaml.engine.v1.exceptions.Mark;
import org.snakeyaml.engine.v1.exceptions.ParserException;
import org.snakeyaml.engine.v1.exceptions.YamlEngineException;
import org.snakeyaml.engine.v1.nodes.Tag;
import org.snakeyaml.engine.v1.scanner.Scanner;
import org.snakeyaml.engine.v1.scanner.ScannerImpl;
import org.snakeyaml.engine.v1.scanner.StreamReader;
import org.snakeyaml.engine.v1.tokens.AliasToken;
import org.snakeyaml.engine.v1.tokens.AnchorToken;
import org.snakeyaml.engine.v1.tokens.BlockEntryToken;
import org.snakeyaml.engine.v1.tokens.DirectiveToken;
import org.snakeyaml.engine.v1.tokens.ScalarToken;
import org.snakeyaml.engine.v1.tokens.StreamEndToken;
import org.snakeyaml.engine.v1.tokens.StreamStartToken;
import org.snakeyaml.engine.v1.tokens.TagToken;
import org.snakeyaml.engine.v1.tokens.TagTuple;
import org.snakeyaml.engine.v1.tokens.Token;

/**
 * <pre>
 * # The following YAML grammar is LL(1) and is parsed by a recursive descent
 * parser.
 * stream            ::= STREAM-START implicit_document? explicit_document* STREAM-END
 * implicit_document ::= block_node DOCUMENT-END*
 * explicit_document ::= DIRECTIVE* DOCUMENT-START block_node? DOCUMENT-END*
 * block_node_or_indentless_sequence ::=
 *                       ALIAS
 *                       | properties (block_content | indentless_block_sequence)?
 *                       | block_content
 *                       | indentless_block_sequence
 * block_node        ::= ALIAS
 *                       | properties block_content?
 *                       | block_content
 * flow_node         ::= ALIAS
 *                       | properties flow_content?
 *                       | flow_content
 * properties        ::= TAG ANCHOR? | ANCHOR TAG?
 * block_content     ::= block_collection | flow_collection | SCALAR
 * flow_content      ::= flow_collection | SCALAR
 * block_collection  ::= block_sequence | block_mapping
 * flow_collection   ::= flow_sequence | flow_mapping
 * block_sequence    ::= BLOCK-SEQUENCE-START (BLOCK-ENTRY block_node?)* BLOCK-END
 * indentless_sequence   ::= (BLOCK-ENTRY block_node?)+
 * block_mapping     ::= BLOCK-MAPPING_START
 *                       ((KEY block_node_or_indentless_sequence?)?
 *                       (VALUE block_node_or_indentless_sequence?)?)*
 *                       BLOCK-END
 * flow_sequence     ::= FLOW-SEQUENCE-START
 *                       (flow_sequence_entry FLOW-ENTRY)*
 *                       flow_sequence_entry?
 *                       FLOW-SEQUENCE-END
 * flow_sequence_entry   ::= flow_node | KEY flow_node? (VALUE flow_node?)?
 * flow_mapping      ::= FLOW-MAPPING-START
 *                       (flow_mapping_entry FLOW-ENTRY)*
 *                       flow_mapping_entry?
 *                       FLOW-MAPPING-END
 * flow_mapping_entry    ::= flow_node | KEY flow_node? (VALUE flow_node?)?
 * FIRST sets:
 * stream: { STREAM-START }
 * explicit_document: { DIRECTIVE DOCUMENT-START }
 * implicit_document: FIRST(block_node)
 * block_node: { ALIAS TAG ANCHOR SCALAR BLOCK-SEQUENCE-START BLOCK-MAPPING-START FLOW-SEQUENCE-START FLOW-MAPPING-START }
 * flow_node: { ALIAS ANCHOR TAG SCALAR FLOW-SEQUENCE-START FLOW-MAPPING-START }
 * block_content: { BLOCK-SEQUENCE-START BLOCK-MAPPING-START FLOW-SEQUENCE-START FLOW-MAPPING-START SCALAR }
 * flow_content: { FLOW-SEQUENCE-START FLOW-MAPPING-START SCALAR }
 * block_collection: { BLOCK-SEQUENCE-START BLOCK-MAPPING-START }
 * flow_collection: { FLOW-SEQUENCE-START FLOW-MAPPING-START }
 * block_sequence: { BLOCK-SEQUENCE-START }
 * block_mapping: { BLOCK-MAPPING-START }
 * block_node_or_indentless_sequence: { ALIAS ANCHOR TAG SCALAR BLOCK-SEQUENCE-START BLOCK-MAPPING-START FLOW-SEQUENCE-START FLOW-MAPPING-START BLOCK-ENTRY }
 * indentless_sequence: { ENTRY }
 * flow_collection: { FLOW-SEQUENCE-START FLOW-MAPPING-START }
 * flow_sequence: { FLOW-SEQUENCE-START }
 * flow_mapping: { FLOW-MAPPING-START }
 * flow_sequence_entry: { ALIAS ANCHOR TAG SCALAR FLOW-SEQUENCE-START FLOW-MAPPING-START KEY }
 * flow_mapping_entry: { ALIAS ANCHOR TAG SCALAR FLOW-SEQUENCE-START FLOW-MAPPING-START KEY }
 * </pre>
 * <p>
 * Since writing a recursive-descendant parser is a straightforward task, we do
 * not give many comments here.
 */
public class ParserImpl implements Parser {
    private static final Map<String, String> DEFAULT_TAGS = new HashMap();

    static {
        DEFAULT_TAGS.put("!", "!");
        DEFAULT_TAGS.put("!!", Tag.PREFIX);
    }

    protected final Scanner scanner;
    private final LoadSettings settings;
    private Optional<Event> currentEvent;
    private final ArrayStack<Production> states;
    private final ArrayStack<Optional<Mark>> marksStack;
    private Optional<Production> state;
    private VersionTagsTuple directives;

    public ParserImpl(StreamReader reader, LoadSettings settings) {
        this(new ScannerImpl(reader), settings);
    }

    public ParserImpl(Scanner scanner, LoadSettings settings) {
        this.scanner = scanner;
        this.settings = settings;
        currentEvent = Optional.empty();
        directives = new VersionTagsTuple(Optional.empty(), new HashMap(DEFAULT_TAGS));
        states = new ArrayStack(100);
        marksStack = new ArrayStack(10);
        state = Optional.of(new ParseStreamStart());
    }

    /**
     * Check the type of the next event.
     */
    public boolean checkEvent(Event.ID choice) {
        peekEvent();
//        return currentEvent.filter(event -> event.isEvent(choice)).isPresent();
        return currentEvent.isPresent() && currentEvent.get().isEvent(choice);
    }

    private void produce() {
        if (!currentEvent.isPresent()) {
            state.ifPresent(prod -> currentEvent = Optional.of(prod.produce()));
        }
    }

    /**
     * Get the next event.
     */
    public Event peekEvent() {
        produce();
        return currentEvent.orElseThrow(() -> new ParserException("Event expected.", Optional.empty()));
    }

    /**
     * Get the next event and proceed further.
     */
    public Event next() {
        peekEvent();
        Event value = currentEvent.get();
        currentEvent = Optional.empty();
        return value;
    }

    @Override
    public boolean hasNext() {
        produce();
        return currentEvent.isPresent();
    }

    /**
     * <pre>
     * stream    ::= STREAM-START implicit_document? explicit_document* STREAM-END
     * implicit_document ::= block_node DOCUMENT-END*
     * explicit_document ::= DIRECTIVE* DOCUMENT-START block_node? DOCUMENT-END*
     * </pre>
     */
    private class ParseStreamStart implements Production {
        public Event produce() {
            // Parse the stream start.
            StreamStartToken token = (StreamStartToken) scanner.next();
            Event event = new StreamStartEvent(token.getStartMark(), token.getEndMark());
            // Prepare the next state.
            state = Optional.of(new ParseImplicitDocumentStart());
            return event;
        }
    }

    private class ParseImplicitDocumentStart implements Production {
        public Event produce() {
            // Parse an implicit document.
            if (!scanner.checkToken(Token.ID.Directive, Token.ID.DocumentStart, Token.ID.StreamEnd)) {
                directives = new VersionTagsTuple(Optional.empty(), DEFAULT_TAGS);
                Token token = scanner.peekToken();
                Optional<Mark> startMark = token.getStartMark();
                Optional<Mark> endMark = startMark;
                Event event = new DocumentStartEvent(false, Optional.empty(), Collections.emptyMap(), startMark, endMark);
                // Prepare the next state.
                states.push(new ParseDocumentEnd());
                state = Optional.of(new ParseBlockNode());
                return event;
            } else {
                Production p = new ParseDocumentStart();
                return p.produce();
            }
        }
    }

    private class ParseDocumentStart implements Production {
        public Event produce() {
            // Parse any extra document end indicators.
            while (scanner.checkToken(Token.ID.DocumentEnd)) {
                scanner.next();
            }
            // Parse an explicit document.
            Event event;
            if (!scanner.checkToken(Token.ID.StreamEnd)) {
                Token token = scanner.peekToken();
                Optional<Mark> startMark = token.getStartMark();
                VersionTagsTuple tuple = processDirectives();
                if (!scanner.checkToken(Token.ID.DocumentStart)) {
                    throw new ParserException("expected '<document start>', but found '"
                            + scanner.peekToken().getTokenId() + "'", scanner.peekToken().getStartMark());
                }
                token = scanner.next();
                Optional<Mark> endMark = token.getEndMark();
                event = new DocumentStartEvent(true, tuple.getSpecVersion(), tuple.getTags(), startMark, endMark);
                states.push(new ParseDocumentEnd());
                state = Optional.of(new ParseDocumentContent());
            } else {
                // Parse the end of the stream.
                StreamEndToken token = (StreamEndToken) scanner.next();
                event = new StreamEndEvent(token.getStartMark(), token.getEndMark());
                if (!states.isEmpty()) {
                    throw new YamlEngineException("Unexpected end of stream. States left: " + states);
                }
                if (!markEmpty()) {
                    throw new YamlEngineException("Unexpected end of stream. Marks left: " + marksStack);
                }
                state = Optional.empty();
            }
            return event;
        }
    }

    private class ParseDocumentEnd implements Production {
        public Event produce() {
            // Parse the document end.
            Token token = scanner.peekToken();
            Optional<Mark> startMark = token.getStartMark();
            Optional<Mark> endMark = startMark;
            boolean explicit = false;
            if (scanner.checkToken(Token.ID.DocumentEnd)) {
                token = scanner.next();
                endMark = token.getEndMark();
                explicit = true;
            }
            Event event = new DocumentEndEvent(explicit, startMark, endMark);
            // Prepare the next state.
            state = Optional.of(new ParseDocumentStart());
            return event;
        }
    }

    private class ParseDocumentContent implements Production {
        public Event produce() {
            Event event;
            if (scanner.checkToken(Token.ID.Directive, Token.ID.DocumentStart,
                    Token.ID.DocumentEnd, Token.ID.StreamEnd)) {
                event = processEmptyScalar(scanner.peekToken().getStartMark());
                state = Optional.of(states.pop());
                return event;
            } else {
                Production p = new ParseBlockNode();
                return p.produce();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private VersionTagsTuple processDirectives() {
        Optional<SpecVersion> yamlSpecVersion = Optional.empty();
        HashMap<String, String> tagHandles = new HashMap<String, String>();
        while (scanner.checkToken(Token.ID.Directive)) {
            @SuppressWarnings("rawtypes")
            DirectiveToken token = (DirectiveToken) scanner.next();
            if (token.getName().equals(DirectiveToken.YAML_DIRECTIVE)) {
                if (yamlSpecVersion.isPresent()) {
                    throw new ParserException("found duplicate YAML directive", token.getStartMark());
                }
                List<Integer> value = (List<Integer>) token.getValue().get();
                Integer major = value.get(0);
                Integer minor = value.get(1);
                yamlSpecVersion = Optional.of(settings.getVersionFunction().apply(new SpecVersion(major, minor)));
            } else if (token.getName().equals(DirectiveToken.TAG_DIRECTIVE)) {
                List<String> value = (List<String>) token.getValue().get();
                String handle = value.get(0);
                String prefix = value.get(1);
                if (tagHandles.containsKey(handle)) {
                    throw new ParserException("duplicate tag handle " + handle,
                            token.getStartMark());
                }
                tagHandles.put(handle, prefix);
            }
        }
        if (!yamlSpecVersion.isPresent() || !tagHandles.isEmpty()) {
            // directives in the document found - drop the previous
            for (String key : DEFAULT_TAGS.keySet()) {
                // do not overwrite re-defined tags
                if (!tagHandles.containsKey(key)) {
                    tagHandles.put(key, DEFAULT_TAGS.get(key));
                }
            }
            directives = new VersionTagsTuple(yamlSpecVersion, tagHandles);
        }
        return directives;
    }

    /**
     * <pre>
     *  block_node_or_indentless_sequence ::= ALIAS
     *                | properties (block_content | indentless_block_sequence)?
     *                | block_content
     *                | indentless_block_sequence
     *  block_node    ::= ALIAS
     *                    | properties block_content?
     *                    | block_content
     *  flow_node     ::= ALIAS
     *                    | properties flow_content?
     *                    | flow_content
     *  properties    ::= TAG ANCHOR? | ANCHOR TAG?
     *  block_content     ::= block_collection | flow_collection | SCALAR
     *  flow_content      ::= flow_collection | SCALAR
     *  block_collection  ::= block_sequence | block_mapping
     *  flow_collection   ::= flow_sequence | flow_mapping
     * </pre>
     */

    private class ParseBlockNode implements Production {
        public Event produce() {
            return parseNode(true, false);
        }
    }

    private Event parseFlowNode() {
        return parseNode(false, false);
    }

    private Event parseBlockNodeOrIndentlessSequence() {
        return parseNode(true, true);
    }

    private Event parseNode(boolean block, boolean indentlessSequence) {
        Event event;
        Optional<Mark> startMark = Optional.empty();
        Optional<Mark> endMark = Optional.empty();
        Optional<Mark> tagMark = Optional.empty();
        if (scanner.checkToken(Token.ID.Alias)) {
            AliasToken token = (AliasToken) scanner.next();
            event = new AliasEvent(Optional.of(token.getValue()), token.getStartMark(), token.getEndMark());
            state = Optional.of(states.pop());
        } else {
            Optional<Anchor> anchor = Optional.empty();
            TagTuple tagTupleValue = null;
            if (scanner.checkToken(Token.ID.Anchor)) {
                AnchorToken token = (AnchorToken) scanner.next();
                startMark = token.getStartMark();
                endMark = token.getEndMark();
                anchor = Optional.of(token.getValue());
                if (scanner.checkToken(Token.ID.Tag)) {
                    TagToken tagToken = (TagToken) scanner.next();
                    tagMark = tagToken.getStartMark();
                    endMark = tagToken.getEndMark();
                    tagTupleValue = tagToken.getValue();
                }
            } else if (scanner.checkToken(Token.ID.Tag)) {
                TagToken tagToken = (TagToken) scanner.next();
                startMark = tagToken.getStartMark();
                tagMark = startMark;
                endMark = tagToken.getEndMark();
                tagTupleValue = tagToken.getValue();
                if (scanner.checkToken(Token.ID.Anchor)) {
                    AnchorToken token = (AnchorToken) scanner.next();
                    endMark = token.getEndMark();
                    anchor = Optional.of(token.getValue());
                }
            }
            Optional<String> tag = Optional.empty();
            if (tagTupleValue != null) {
                String handle = tagTupleValue.getHandle();
                String suffix = tagTupleValue.getSuffix();
                if (handle != null) {
                    if (!directives.getTags().containsKey(handle)) {
                        throw new ParserException("while parsing a node", startMark,
                                "found undefined tag handle " + handle, tagMark);
                    }
                    tag = Optional.of(directives.getTags().get(handle) + suffix);
                } else {
                    tag = Optional.of(suffix);
                }
            }
            if (!startMark.isPresent()) {
                startMark = scanner.peekToken().getStartMark();
                endMark = startMark;
            }
            boolean implicit = !tag.isPresent() || tag.equals("!");
            if (indentlessSequence && scanner.checkToken(Token.ID.BlockEntry)) {
                endMark = scanner.peekToken().getEndMark();
                event = new SequenceStartEvent(anchor, tag, implicit, FlowStyle.BLOCK, startMark, endMark);
                state = Optional.of(new ParseIndentlessSequenceEntry());
            } else {
                if (scanner.checkToken(Token.ID.Scalar)) {
                    ScalarToken token = (ScalarToken) scanner.next();
                    endMark = token.getEndMark();
                    ImplicitTuple implicitValues;
                    if ((token.isPlain() && !tag.isPresent()) || "!".equals(tag)) {
                        implicitValues = new ImplicitTuple(true, false);
                    } else if (!tag.isPresent()) {
                        implicitValues = new ImplicitTuple(false, true);
                    } else {
                        implicitValues = new ImplicitTuple(false, false);
                    }
                    event = new ScalarEvent(anchor, tag, implicitValues, token.getValue(), token.getStyle(),
                            startMark, endMark);
                    state = Optional.of(states.pop());
                } else if (scanner.checkToken(Token.ID.FlowSequenceStart)) {
                    endMark = scanner.peekToken().getEndMark();
                    event = new SequenceStartEvent(anchor, tag, implicit, FlowStyle.FLOW, startMark, endMark);
                    state = Optional.of(new ParseFlowSequenceFirstEntry());
                } else if (scanner.checkToken(Token.ID.FlowMappingStart)) {
                    endMark = scanner.peekToken().getEndMark();
                    event = new MappingStartEvent(anchor, tag, implicit,
                            FlowStyle.FLOW, startMark, endMark);
                    state = Optional.of(new ParseFlowMappingFirstKey());
                } else if (block && scanner.checkToken(Token.ID.BlockSequenceStart)) {
                    endMark = scanner.peekToken().getStartMark();
                    event = new SequenceStartEvent(anchor, tag, implicit, FlowStyle.BLOCK, startMark, endMark);
                    state = Optional.of(new ParseBlockSequenceFirstEntry());
                } else if (block && scanner.checkToken(Token.ID.BlockMappingStart)) {
                    endMark = scanner.peekToken().getStartMark();
                    event = new MappingStartEvent(anchor, tag, implicit,
                            FlowStyle.BLOCK, startMark, endMark);
                    state = Optional.of(new ParseBlockMappingFirstKey());
                } else if (anchor.isPresent() || tag.isPresent()) {
                    // Empty scalars are allowed even if a tag or an anchor is specified.
                    event = new ScalarEvent(anchor, tag, new ImplicitTuple(implicit, false), "", ScalarStyle.PLAIN,
                            startMark, endMark);
                    state = Optional.of(states.pop());
                } else {
                    String node;
                    if (block) {
                        node = "block";
                    } else {
                        node = "flow";
                    }
                    Token token = scanner.peekToken();
                    throw new ParserException("while parsing a " + node + " node", startMark,
                            "expected the node content, but found '" + token.getTokenId() + "'",
                            token.getStartMark());
                }
            }
        }
        return event;
    }

    // block_sequence ::= BLOCK-SEQUENCE-START (BLOCK-ENTRY block_node?)*
    // BLOCK-END

    private class ParseBlockSequenceFirstEntry implements Production {
        public Event produce() {
            Token token = scanner.next();
            markPush(token.getStartMark());
            return new ParseBlockSequenceEntry().produce();
        }
    }

    private class ParseBlockSequenceEntry implements Production {
        public Event produce() {
            if (scanner.checkToken(Token.ID.BlockEntry)) {
                BlockEntryToken token = (BlockEntryToken) scanner.next();
                if (!scanner.checkToken(Token.ID.BlockEntry, Token.ID.BlockEnd)) {
                    states.push(new ParseBlockSequenceEntry());
                    return new ParseBlockNode().produce();
                } else {
                    state = Optional.of(new ParseBlockSequenceEntry());
                    return processEmptyScalar(token.getEndMark());
                }
            }
            if (!scanner.checkToken(Token.ID.BlockEnd)) {
                Token token = scanner.peekToken();
                throw new ParserException("while parsing a block collection", markPop(),
                        "expected <block end>, but found '" + token.getTokenId() + "'",
                        token.getStartMark());
            }
            Token token = scanner.next();
            Event event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());
            state = Optional.of(states.pop());
            markPop();
            return event;
        }
    }

    // indentless_sequence ::= (BLOCK-ENTRY block_node?)+

    private class ParseIndentlessSequenceEntry implements Production {
        public Event produce() {
            if (scanner.checkToken(Token.ID.BlockEntry)) {
                Token token = scanner.next();
                if (!scanner.checkToken(Token.ID.BlockEntry, Token.ID.Key, Token.ID.Value,
                        Token.ID.BlockEnd)) {
                    states.push(new ParseIndentlessSequenceEntry());
                    return new ParseBlockNode().produce();
                } else {
                    state = Optional.of(new ParseIndentlessSequenceEntry());
                    return processEmptyScalar(token.getEndMark());
                }
            }
            Token token = scanner.peekToken();
            Event event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());
            state = Optional.of(states.pop());
            return event;
        }
    }

    private class ParseBlockMappingFirstKey implements Production {
        public Event produce() {
            Token token = scanner.next();
            markPush(token.getStartMark());
            return new ParseBlockMappingKey().produce();
        }
    }

    private class ParseBlockMappingKey implements Production {
        public Event produce() {
            if (scanner.checkToken(Token.ID.Key)) {
                Token token = scanner.next();
                if (!scanner.checkToken(Token.ID.Key, Token.ID.Value, Token.ID.BlockEnd)) {
                    states.push(new ParseBlockMappingValue());
                    return parseBlockNodeOrIndentlessSequence();
                } else {
                    state = Optional.of(new ParseBlockMappingValue());
                    return processEmptyScalar(token.getEndMark());
                }
            }
            if (!scanner.checkToken(Token.ID.BlockEnd)) {
                Token token = scanner.peekToken();
                throw new ParserException("while parsing a block mapping", markPop(),
                        "expected <block end>, but found '" + token.getTokenId() + "'",
                        token.getStartMark());
            }
            Token token = scanner.next();
            Event event = new MappingEndEvent(token.getStartMark(), token.getEndMark());
            state = Optional.of(states.pop());
            markPop();
            return event;
        }
    }

    private class ParseBlockMappingValue implements Production {
        public Event produce() {
            if (scanner.checkToken(Token.ID.Value)) {
                Token token = scanner.next();
                if (!scanner.checkToken(Token.ID.Key, Token.ID.Value, Token.ID.BlockEnd)) {
                    states.push(new ParseBlockMappingKey());
                    return parseBlockNodeOrIndentlessSequence();
                } else {
                    state = Optional.of(new ParseBlockMappingKey());
                    return processEmptyScalar(token.getEndMark());
                }
            }
            state = Optional.of(new ParseBlockMappingKey());
            Token token = scanner.peekToken();
            return processEmptyScalar(token.getStartMark());
        }
    }

    /**
     * <pre>
     * flow_sequence     ::= FLOW-SEQUENCE-START
     *                       (flow_sequence_entry FLOW-ENTRY)*
     *                       flow_sequence_entry?
     *                       FLOW-SEQUENCE-END
     * flow_sequence_entry   ::= flow_node | KEY flow_node? (VALUE flow_node?)?
     * Note that while production rules for both flow_sequence_entry and
     * flow_mapping_entry are equal, their interpretations are different.
     * For `flow_sequence_entry`, the part `KEY flow_node? (VALUE flow_node?)?`
     * generate an inline mapping (set syntax).
     * </pre>
     */
    private class ParseFlowSequenceFirstEntry implements Production {
        public Event produce() {
            Token token = scanner.next();
            markPush(token.getStartMark());
            return new ParseFlowSequenceEntry(true).produce();
        }
    }

    private class ParseFlowSequenceEntry implements Production {
        private boolean first = false;

        public ParseFlowSequenceEntry(boolean first) {
            this.first = first;
        }

        public Event produce() {
            if (!scanner.checkToken(Token.ID.FlowSequenceEnd)) {
                if (!first) {
                    if (scanner.checkToken(Token.ID.FlowEntry)) {
                        scanner.next();
                    } else {
                        Token token = scanner.peekToken();
                        throw new ParserException("while parsing a flow sequence", markPop(),
                                "expected ',' or ']', but got " + token.getTokenId(),
                                token.getStartMark());
                    }
                }
                if (scanner.checkToken(Token.ID.Key)) {
                    Token token = scanner.peekToken();
                    Event event = new MappingStartEvent(Optional.empty(), Optional.empty(), true, FlowStyle.FLOW, token.getStartMark(),
                            token.getEndMark());
                    state = Optional.of(new ParseFlowSequenceEntryMappingKey());
                    return event;
                } else if (!scanner.checkToken(Token.ID.FlowSequenceEnd)) {
                    states.push(new ParseFlowSequenceEntry(false));
                    return parseFlowNode();
                }
            }
            Token token = scanner.next();
            Event event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());
            state = Optional.of(states.pop());
            markPop();
            return event;
        }
    }

    private class ParseFlowSequenceEntryMappingKey implements Production {
        public Event produce() {
            Token token = scanner.next();
            if (!scanner.checkToken(Token.ID.Value, Token.ID.FlowEntry, Token.ID.FlowSequenceEnd)) {
                states.push(new ParseFlowSequenceEntryMappingValue());
                return parseFlowNode();
            } else {
                state = Optional.of(new ParseFlowSequenceEntryMappingValue());
                return processEmptyScalar(token.getEndMark());
            }
        }
    }

    private class ParseFlowSequenceEntryMappingValue implements Production {
        public Event produce() {
            if (scanner.checkToken(Token.ID.Value)) {
                Token token = scanner.next();
                if (!scanner.checkToken(Token.ID.FlowEntry, Token.ID.FlowSequenceEnd)) {
                    states.push(new ParseFlowSequenceEntryMappingEnd());
                    return parseFlowNode();
                } else {
                    state = Optional.of(new ParseFlowSequenceEntryMappingEnd());
                    return processEmptyScalar(token.getEndMark());
                }
            } else {
                state = Optional.of(new ParseFlowSequenceEntryMappingEnd());
                Token token = scanner.peekToken();
                return processEmptyScalar(token.getStartMark());
            }
        }
    }

    private class ParseFlowSequenceEntryMappingEnd implements Production {
        public Event produce() {
            state = Optional.of(new ParseFlowSequenceEntry(false));
            Token token = scanner.peekToken();
            return new MappingEndEvent(token.getStartMark(), token.getEndMark());
        }
    }

    /**
     * <pre>
     *   flow_mapping  ::= FLOW-MAPPING-START
     *          (flow_mapping_entry FLOW-ENTRY)*
     *          flow_mapping_entry?
     *          FLOW-MAPPING-END
     *   flow_mapping_entry    ::= flow_node | KEY flow_node? (VALUE flow_node?)?
     * </pre>
     */
    private class ParseFlowMappingFirstKey implements Production {
        public Event produce() {
            Token token = scanner.next();
            markPush(token.getStartMark());
            return new ParseFlowMappingKey(true).produce();
        }
    }

    private class ParseFlowMappingKey implements Production {
        private boolean first = false;

        public ParseFlowMappingKey(boolean first) {
            this.first = first;
        }

        public Event produce() {
            if (!scanner.checkToken(Token.ID.FlowMappingEnd)) {
                if (!first) {
                    if (scanner.checkToken(Token.ID.FlowEntry)) {
                        scanner.next();
                    } else {
                        Token token = scanner.peekToken();
                        throw new ParserException("while parsing a flow mapping", markPop(),
                                "expected ',' or '}', but got " + token.getTokenId(),
                                token.getStartMark());
                    }
                }
                if (scanner.checkToken(Token.ID.Key)) {
                    Token token = scanner.next();
                    if (!scanner.checkToken(Token.ID.Value, Token.ID.FlowEntry,
                            Token.ID.FlowMappingEnd)) {
                        states.push(new ParseFlowMappingValue());
                        return parseFlowNode();
                    } else {
                        state = Optional.of(new ParseFlowMappingValue());
                        return processEmptyScalar(token.getEndMark());
                    }
                } else if (!scanner.checkToken(Token.ID.FlowMappingEnd)) {
                    states.push(new ParseFlowMappingEmptyValue());
                    return parseFlowNode();
                }
            }
            Token token = scanner.next();
            Event event = new MappingEndEvent(token.getStartMark(), token.getEndMark());
            state = Optional.of(states.pop());
            markPop();
            return event;
        }
    }

    private class ParseFlowMappingValue implements Production {
        public Event produce() {
            if (scanner.checkToken(Token.ID.Value)) {
                Token token = scanner.next();
                if (!scanner.checkToken(Token.ID.FlowEntry, Token.ID.FlowMappingEnd)) {
                    states.push(new ParseFlowMappingKey(false));
                    return parseFlowNode();
                } else {
                    state = Optional.of(new ParseFlowMappingKey(false));
                    return processEmptyScalar(token.getEndMark());
                }
            } else {
                state = Optional.of(new ParseFlowMappingKey(false));
                Token token = scanner.peekToken();
                return processEmptyScalar(token.getStartMark());
            }
        }
    }

    private class ParseFlowMappingEmptyValue implements Production {
        public Event produce() {
            state = Optional.of(new ParseFlowMappingKey(false));
            return processEmptyScalar(scanner.peekToken().getStartMark());
        }
    }

    /**
     * <pre>
     * block_mapping     ::= BLOCK-MAPPING_START
     *           ((KEY block_node_or_indentless_sequence?)?
     *           (VALUE block_node_or_indentless_sequence?)?)*
     *           BLOCK-END
     * </pre>
     */
    private Event processEmptyScalar(Optional<Mark> mark) {
        return new ScalarEvent(Optional.empty(), Optional.empty(), new ImplicitTuple(true, false), "", ScalarStyle.PLAIN, mark, mark);
    }

    private Optional<Mark> markPop() {
        return marksStack.pop();
    }

    private void markPush(Optional<Mark> mark) {
        marksStack.push(mark);
    }

    private boolean markEmpty() {
        return marksStack.isEmpty();
    }


}
