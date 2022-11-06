
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = Pattern.MyDeserializer.class)
public class Pattern {

    /**
     * Type: {@code String | PatternObject}
     * 
     */
    private Object value;

    /**
     * Type: {@code String | PatternObject}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code String | PatternObject}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<Pattern>
    {


        @Override
        public Pattern deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            Pattern result = new Pattern();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(String.class);
            } else {
                if (token == JsonToken.START_OBJECT) {
                    result.value = parser.readValueAs(PatternObject.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
