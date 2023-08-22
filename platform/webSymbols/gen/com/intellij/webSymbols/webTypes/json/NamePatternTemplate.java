
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = NamePatternTemplate.MyDeserializer.class)
public class NamePatternTemplate {

    /**
     * Type: {@code String | List<NamePatternTemplate> | NamePatternBase}
     * 
     */
    private Object value;

    /**
     * Type: {@code String | List<NamePatternTemplate> | NamePatternBase}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code String | List<NamePatternTemplate> | NamePatternBase}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<NamePatternTemplate>
    {


        @Override
        public NamePatternTemplate deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            NamePatternTemplate result = new NamePatternTemplate();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(String.class);
            } else {
                if (token == JsonToken.START_ARRAY) {
                    result.value = parser.getCodec().readValue(parser, deserializationContext.getTypeFactory().constructParametricType(List.class, NamePatternTemplate.class));
                } else {
                    if (token == JsonToken.START_OBJECT) {
                        result.value = parser.readValueAs(NamePatternBase.class);
                    } else {
                        deserializationContext.handleUnexpectedToken(Object.class, parser);
                    }
                }
            }
            return result;
        }

    }

}
