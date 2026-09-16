
package com.intellij.polySymbols.webTypes.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = Priority.MyDeserializer.class)
public class Priority {

    /**
     * Type: {@code Double | PriorityLevel}
     * 
     */
    private Object value;

    /**
     * Type: {@code Double | PriorityLevel}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code Double | PriorityLevel}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<Priority>
    {


        @Override
        public Priority deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            Priority result = new Priority();
            JsonToken token = parser.currentToken();
            if ((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT)) {
                result.value = parser.readValueAs(Double.class);
            } else {
                if (token == JsonToken.VALUE_STRING) {
                    result.value = parser.readValueAs(Priority.PriorityLevel.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

    public enum PriorityLevel {

        LOWEST("lowest"),
        LOW("low"),
        NORMAL("normal"),
        HIGH("high"),
        HIGHEST("highest");
        private final String value;
        private final static Map<String, Priority.PriorityLevel> CONSTANTS = new HashMap<String, Priority.PriorityLevel>();

        static {
            for (Priority.PriorityLevel c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private PriorityLevel(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static Priority.PriorityLevel fromValue(String value) {
            Priority.PriorityLevel constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
