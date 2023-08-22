
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = Experimental.MyDeserializer.class)
public class Experimental {

    /**
     * Type: {@code Boolean | String}
     * 
     */
    private Object value;

    /**
     * Type: {@code Boolean | String}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code Boolean | String}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<Experimental>
    {


        @Override
        public Experimental deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            Experimental result = new Experimental();
            JsonToken token = parser.currentToken();
            if ((token == JsonToken.VALUE_TRUE)||(token == JsonToken.VALUE_FALSE)) {
                result.value = parser.readValueAs(Boolean.class);
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
