import abc
import io
import tokenize
from typing import List, Type, Optional
from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase
from data_processing.processors.black_styler import BlackStylerBase
from data_processing.processors.empty_line_remover import EmptyLineRemoverBase
class CommentRemoverBase(ProcessorBase):⇥@abc.abstractmethod
def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥raise NotImplementedError⇤@staticmethod
def is_deterministic() -> bool:⇥return True⇤@staticmethod
def is_slow() -> bool:⇥return False⇤@staticmethod
def must_be_after() -> List[Type[ProcessorBase]]:⇥return []⇤@staticmethod
def must_be_before() -> List[Type[ProcessorBase]]:⇥return [BlackStylerBase, EmptyLineRemoverBase]⇤@staticmethod
def _remove_comments(dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥try:⇥token_gen = tokenize.generate_tokens(io.StringIO(dp.content).readline)
result = []
last_lineno = -1
last_col = 0
skip_new_line = False
for token_type, token_string, (start_line, start_col), (end_line, end_col), ltext in token_gen:⇥if start_line > last_lineno:⇥last_col = 0⇤if not token_type == tokenize.COMMENT and start_col > last_col:⇥result.append(" " * (start_col - last_col))⇤if token_type == tokenize.COMMENT:⇥assert start_line == end_line
if token_string.strip() == ltext.strip():⇥skip_new_line = True⇤⇤else:⇥if skip_new_line:⇥skip_new_line = False⇤else:⇥result.append(token_string)⇤⇤last_col = end_col
last_lineno = end_line⇤⇤except (tokenize.TokenError, IndentationError):⇥return None⇤dp.content = "".join(result)
return dp⇤⇤class CommentRemoverTrainData(CommentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._remove_comments(dp)⇤⇤class CommentRemoverTrainModel(CommentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._remove_comments(dp)⇤⇤class CommentRemoverInference(CommentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return dp⇤⇤