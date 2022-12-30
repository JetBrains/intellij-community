
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = NamePatternRoot.MyDeserializer.class)
public class NamePatternRoot {

    /**
     * Type: {@code NamePatternBase | String}
     * 
     */
    private Object value;

    /**
     * Type: {@code NamePatternBase | String}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code NamePatternBase | String}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<NamePatternRoot>
    {


        @Override
        public NamePatternRoot deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            NamePatternRoot result = new NamePatternRoot();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_OBJECT) {
                result.value = parser.readValueAs(NamePatternBase.class);
            } else {
                if (token == JsonToken.VALUE_STRING) {
                    result.value = parser.readValueAs(String.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
