
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = ListReference.MyDeserializer.class)
public class ListReference
    extends ArrayList<Reference>
{


    public static class MyDeserializer
        extends JsonDeserializer<ListReference>
    {


        @Override
        public ListReference deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            ListReference result = new ListReference();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken()!= JsonToken.END_ARRAY) {
                    token = parser.currentToken();
                    if ((token == JsonToken.VALUE_STRING)||(token == JsonToken.START_OBJECT)) {
                        result.add(parser.readValueAs(Reference.class));
                    } else {
                        deserializationContext.handleUnexpectedToken(Reference.class, parser);
                    }
                }
            } else {
                if ((token == JsonToken.VALUE_STRING)||(token == JsonToken.START_OBJECT)) {
                    result.add(parser.readValueAs(Reference.class));
                } else {
                    deserializationContext.handleUnexpectedToken(Reference.class, parser);
                }
            }
            return result;
        }

    }

}
