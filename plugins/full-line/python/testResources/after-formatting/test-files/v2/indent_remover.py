import abc
from typing import Optional, List, Type
from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase
from data_processing.processors.black_styler import BlackStylerBase
class IndentRemoverBase(ProcessorBase):⇥def __init__(self, scope_in_symbol: str, scope_out_symbol: str):⇥self._scope_in_symbol = scope_in_symbol
self._scope_out_symbol = scope_out_symbol⇤@property
def scope_in_symbol(self) -> str:⇥return self._scope_in_symbol⇤@property
def scope_out_symbol(self) -> str:⇥return self._scope_out_symbol⇤@abc.abstractmethod
def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥raise NotImplementedError⇤@staticmethod
def is_deterministic() -> bool:⇥return True⇤@staticmethod
def is_slow() -> bool:⇥return False⇤@staticmethod
def must_be_after() -> List[Type[ProcessorBase]]:⇥return [BlackStylerBase]⇤@staticmethod
def must_be_before() -> List[Type[ProcessorBase]]:⇥return []⇤@staticmethod
def _remove_indents(⇥dp: CodeDataPoint, newline_scope_in_char: str, newline_scope_out_char: str⇤) -> Optional[CodeDataPoint]:⇥content_lines = dp.content.split("\n")
if not content_lines:⇥return dp⇤prev_indent_level = 0
if not content_lines[0].lstrip() == content_lines[0]:⇥return None⇤lines = [content_lines[0]]
for line in content_lines[1:]:⇥stripped_line = line.lstrip()
cur_indent_level = (len(line) - len(stripped_line)) / 4
indent_change = cur_indent_level - prev_indent_level
if indent_change == 0:⇥lines.append("\n")⇤elif indent_change == 1:⇥lines.append(newline_scope_in_char)⇤elif indent_change < 0 and indent_change.is_integer():⇥lines.append(newline_scope_out_char * -int(indent_change))⇤elif indent_change > 0:⇥lines.append(newline_scope_in_char)⇤elif indent_change < 0:⇥lines.append(newline_scope_out_char)⇤else:⇥raise ValueError("Indent change is not a number")⇤lines.append(stripped_line)
prev_indent_level = cur_indent_level⇤dp.content = "".join(lines)
return dp⇤⇤class IndentRemoverTrainData(IndentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._remove_indents(dp, f"\n{self._scope_in_symbol}\n", f"\n{self._scope_out_symbol}\n")⇤⇤class IndentRemoverTrainModel(IndentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._remove_indents(dp, self._scope_in_symbol, self._scope_out_symbol)⇤⇤class IndentRemoverInference(IndentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._remove_indents(dp, self._scope_in_symbol, self._scope_out_symbol)⇤⇤